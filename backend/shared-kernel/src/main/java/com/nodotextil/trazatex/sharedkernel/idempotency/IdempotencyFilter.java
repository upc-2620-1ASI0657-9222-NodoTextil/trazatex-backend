package com.nodotextil.trazatex.sharedkernel.idempotency;

import com.nodotextil.trazatex.sharedkernel.error.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String USER_ID_HEADER = "X-User-Id";
    public static final Duration DEFAULT_RETENTION = Duration.ofHours(24);

    public static final String MISSING_USER_ID = "MISSING_USER_ID";
    public static final String INVALID_IDEMPOTENCY_KEY = "INVALID_IDEMPOTENCY_KEY";
    public static final String IDEMPOTENCY_KEY_REUSED = "IDEMPOTENCY_KEY_REUSED";
    public static final String OPERATION_ROLLED_BACK = "OPERATION_ROLLED_BACK";

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH");
    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final int MAX_RESERVE_ATTEMPTS = 3;

    private final IdempotencyStorePort store;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Duration retention;
    private final Predicate<HttpServletRequest> bypass;

    public IdempotencyFilter(IdempotencyStorePort store, PlatformTransactionManager transactionManager,
                             ObjectMapper objectMapper) {
        this(store, transactionManager, objectMapper, Clock.systemUTC(), DEFAULT_RETENTION);
    }

    public IdempotencyFilter(IdempotencyStorePort store, PlatformTransactionManager transactionManager,
                             ObjectMapper objectMapper, Clock clock, Duration retention) {
        this(store, transactionManager, objectMapper, clock, retention, request -> false);
    }

    public IdempotencyFilter(IdempotencyStorePort store, PlatformTransactionManager transactionManager,
                             ObjectMapper objectMapper, Clock clock, Duration retention,
                             Predicate<HttpServletRequest> bypass) {
        this.bypass = bypass;
        this.store = store;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.retention = retention;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getHeader(IDEMPOTENCY_KEY_HEADER);
        if (!WRITE_METHODS.contains(request.getMethod()) || key == null || bypass.test(request)) {
            chain.doFilter(request, response);
            return;
        }
        String userId = request.getHeader(USER_ID_HEADER);
        if (userId == null || userId.isBlank()) {
            writeJson(response, HttpServletResponse.SC_BAD_REQUEST,
                    error(MISSING_USER_ID, "La cabecera " + USER_ID_HEADER + " es obligatoria en escrituras"));
            return;
        }
        if (!UUID_PATTERN.matcher(key).matches()) {
            writeJson(response, HttpServletResponse.SC_BAD_REQUEST,
                    error(INVALID_IDEMPOTENCY_KEY, "La cabecera " + IDEMPOTENCY_KEY_HEADER + " debe ser un UUID"));
            return;
        }

        HttpServletRequest cachedRequest;
        String requestHash;
        if (isMultipart(request)) {
            try {
                requestHash = hashParts(request);
            } catch (IOException | ServletException | RuntimeException unreadable) {
                chain.doFilter(request, response);
                return;
            }
            cachedRequest = request;
        } else {
            byte[] body = request.getInputStream().readAllBytes();
            requestHash = hash(request, body);
            cachedRequest = new CachedBodyHttpServletRequest(request, body);
        }
        ContentCachingResponseWrapper cachedResponse = new ContentCachingResponseWrapper(response);

        Reply reply;
        try {
            reply = transactionTemplate.execute(
                    tx -> process(tx, key, userId, requestHash, cachedRequest, cachedResponse, chain));
        } catch (UnexpectedRollbackException e) {
            reply = new Reply(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, toJson(error(OPERATION_ROLLED_BACK,
                    "La operación se revirtió; puede reintentar con la misma " + IDEMPOTENCY_KEY_HEADER)));
        } catch (ChainFailure e) {
            if (e.getCause() instanceof IOException io) {
                throw io;
            }
            throw (ServletException) e.getCause();
        }

        if (reply == null) {
            cachedResponse.copyBodyToResponse();
        } else {
            writeJson(response, reply.status(), reply.body());
        }
    }

    private Reply process(TransactionStatus tx, String key, String userId, String requestHash,
                          HttpServletRequest request, ContentCachingResponseWrapper response, FilterChain chain) {
        Reply existing = reserveOrReply(key, userId, requestHash);
        if (existing != null) {
            return existing;
        }
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException e) {
            throw new ChainFailure(e);
        }
        int status = response.getStatus();
        if (status < 200 || status >= 300) {
            tx.setRollbackOnly();
            return null;
        }
        store.complete(key, status, jsonBodyOf(response));
        return null;
    }

    private Reply reserveOrReply(String key, String userId, String requestHash) {
        for (int attempt = 0; attempt < MAX_RESERVE_ATTEMPTS; attempt++) {
            if (store.tryReserve(key, userId, requestHash, clock.instant())) {
                return null;
            }
            Optional<IdempotencyRecord> found = store.find(key);
            if (found.isEmpty()) {
                continue;
            }
            IdempotencyRecord record = found.get();
            if (record.createdAt().plus(retention).isBefore(clock.instant())) {
                store.delete(key);
                continue;
            }
            return replyFor(record, userId, requestHash);
        }
        throw new IllegalStateException("No se pudo reservar la clave de idempotencia " + key);
    }

    private Reply replyFor(IdempotencyRecord record, String userId, String requestHash) {
        if (!record.userId().equals(userId) || !record.requestHash().equals(requestHash)) {
            return new Reply(422, toJson(error(IDEMPOTENCY_KEY_REUSED,
                    "La " + IDEMPOTENCY_KEY_HEADER + " ya se usó con otra petición")));
        }
        if (record.responseStatus() <= 0) {
            throw new IllegalStateException("Clave de idempotencia sin respuesta guardada: " + record.key());
        }
        return new Reply(record.responseStatus(), record.responseBody());
    }

    private static String jsonBodyOf(ContentCachingResponseWrapper response) {
        byte[] content = response.getContentAsByteArray();
        String contentType = response.getContentType();
        if (content.length == 0 || contentType == null || !contentType.toLowerCase(Locale.ROOT).contains("json")) {
            return null;
        }
        return new String(content, StandardCharsets.UTF_8);
    }

    private static boolean isMultipart(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("multipart/");
    }

    private static String hashParts(HttpServletRequest request) throws IOException, ServletException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String query = request.getQueryString();
            String target = request.getMethod() + " " + request.getRequestURI() + (query == null ? "" : "?" + query) + "\nmultipart\n";
            digest.update(target.getBytes(StandardCharsets.UTF_8));
            byte[] buffer = new byte[8192];
            for (Part part : request.getParts()) {
                updateText(digest, part.getName());
                updateText(digest, part.getSubmittedFileName());
                updateText(digest, part.getContentType());
                digest.update(ByteBuffer.allocate(Long.BYTES).putLong(part.getSize()).array());
                try (InputStream content = part.getInputStream()) {
                    for (int read = content.read(buffer); read >= 0; read = content.read(buffer)) {
                        digest.update(buffer, 0, read);
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void updateText(MessageDigest digest, String text) {
        byte[] bytes = text == null ? new byte[0] : text.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES + 1).putInt(bytes.length).put((byte) (text == null ? 0 : 1)).array());
        digest.update(bytes);
    }

    private static String hash(HttpServletRequest request, byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String query = request.getQueryString();
            String target = request.getMethod() + " " + request.getRequestURI() + (query == null ? "" : "?" + query) + "\n";
            digest.update(target.getBytes(StandardCharsets.UTF_8));
            digest.update(body);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ApiError error(String code, String message) {
        return ApiError.of(code, message);
    }

    private String toJson(ApiError error) {
        return objectMapper.writeValueAsString(error);
    }

    private void writeJson(HttpServletResponse response, int status, ApiError error) throws IOException {
        writeJson(response, status, toJson(error));
    }

    private void writeJson(HttpServletResponse response, int status, String body) throws IOException {
        response.setStatus(status);
        if (body != null) {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        }
    }

    private record Reply(int status, String body) {
    }

    private static class ChainFailure extends RuntimeException {
        ChainFailure(Exception cause) {
            super(cause);
        }
    }
}
