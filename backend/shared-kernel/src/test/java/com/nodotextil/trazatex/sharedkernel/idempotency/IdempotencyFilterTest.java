package com.nodotextil.trazatex.sharedkernel.idempotency;

import com.nodotextil.trazatex.sharedkernel.testsupport.MutableClock;
import com.nodotextil.trazatex.sharedkernel.testsupport.RecordingTransactionManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockPart;
import jakarta.servlet.http.Part;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyFilterTest {

    private static final String KEY = "6f1c1c1e-3f0e-4b7e-9d58-0c6a5a3d2b11";
    private static final String USER = "0d6c1f6e-8b0a-4a52-8f43-2d5f6c7a9e10";
    private static final String CREATED = "{\"id\":\"b-1\"}";

    private InMemoryIdempotencyStore store;
    private RecordingTransactionManager transactionManager;
    private MutableClock clock;
    private ObjectMapper mapper;
    private IdempotencyFilter filter;
    private AtomicInteger executions;

    @BeforeEach
    void setUp() {
        store = new InMemoryIdempotencyStore();
        transactionManager = new RecordingTransactionManager();
        clock = new MutableClock(Instant.parse("2026-10-19T14:00:00Z"));
        mapper = JsonMapper.builder().build();
        filter = new IdempotencyFilter(store, transactionManager, mapper, clock, Duration.ofHours(24));
        executions = new AtomicInteger();
    }

    // --- Lo que el filtro no toca ---------------------------------------------------------------------------------

    @Test
    void lecturasPasanSinTransaccionNiAlmacen() throws Exception {
        MockHttpServletRequest request = request("GET", "/batches", KEY, USER, "");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, respondingWith(200, "[]"));

        assertThat(executions).hasValue(1);
        assertThat(response.getContentAsString()).isEqualTo("[]");
        assertThat(transactionManager.outcomes()).isEmpty();
        assertThat(store.size()).isZero();
    }

    @Test
    void escrituraSinIdempotencyKeyPasaSinTransaccionNiAlmacen() throws Exception {
        MockHttpServletRequest request = request("POST", "/auth/login", null, null, "{}");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, respondingWith(200, "{\"token\":\"t\"}"));

        assertThat(executions).hasValue(1);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(transactionManager.outcomes()).isEmpty();
        assertThat(store.size()).isZero();
    }

    @Test
    void enUnaRutaPublicaLaClaveSeIgnoraSinExigirXUserIdNiAlmacenarNada() throws Exception {
        IdempotencyFilter publicAware = new IdempotencyFilter(store, transactionManager, mapper, clock,
                Duration.ofHours(24), req -> req.getRequestURI().startsWith("/public/"));
        MockHttpServletRequest request = request("POST", "/public/accept", KEY, null, "{}");
        MockHttpServletResponse response = new MockHttpServletResponse();

        publicAware.doFilter(request, response, respondingWith(201, "{\"id\":\"u-1\"}"));

        assertThat(executions).hasValue(1);
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getContentAsString()).isEqualTo("{\"id\":\"u-1\"}");
        assertThat(transactionManager.outcomes()).isEmpty();
        assertThat(store.size()).isZero();
    }

    @Test
    void enUnaRutaPublicaLaMismaClaveNoReplicaNada() throws Exception {
        IdempotencyFilter publicAware = new IdempotencyFilter(store, transactionManager, mapper, clock,
                Duration.ofHours(24), req -> req.getRequestURI().startsWith("/public/"));

        publicAware.doFilter(request("POST", "/public/accept", KEY, null, "{}"), new MockHttpServletResponse(), respondingWith(201, "{}"));
        publicAware.doFilter(request("POST", "/public/accept", KEY, null, "{}"), new MockHttpServletResponse(), respondingWith(201, "{}"));

        assertThat(executions).hasValue(2);
    }

    @Test
    void lasRutasNoPublicasSiguenExigiendoXUserIdAunqueExistaElBypass() throws Exception {
        IdempotencyFilter publicAware = new IdempotencyFilter(store, transactionManager, mapper, clock,
                Duration.ofHours(24), req -> req.getRequestURI().startsWith("/public/"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        publicAware.doFilter(request("POST", "/private/batches", KEY, null, "{}"), response, respondingWith(201, "{}"));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(executions).hasValue(0);
    }

    // --- Rechazos antes de ejecutar ---------------------------------------------------------------------------------

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void sinXUserIdLaEscrituraSeRechazaConBadRequest(String user) throws Exception {
        MockHttpServletRequest request = request("POST", "/batches", KEY, user, "{}");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, respondingWith(201, CREATED));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(errorCode(response)).isEqualTo(IdempotencyFilter.MISSING_USER_ID);
        assertThat(executions).hasValue(0);
        assertThat(store.size()).isZero();
        assertThat(transactionManager.outcomes()).isEmpty();
    }

    @Test
    void claveQueNoEsUuidSeRechazaConBadRequest() throws Exception {
        MockHttpServletRequest request = request("POST", "/batches", "no-es-un-uuid", USER, "{}");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, respondingWith(201, CREATED));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(errorCode(response)).isEqualTo(IdempotencyFilter.INVALID_IDEMPOTENCY_KEY);
        assertThat(executions).hasValue(0);
    }

    // --- Camino feliz y repetición ---------------------------------------------------------------------------------

    @Test
    void claveNuevaEjecutaUnaVezGuardaLaRespuestaYConfirma() throws Exception {
        MockHttpServletResponse response = send("POST", "/batches", KEY, USER, "{\"kg\":10}", respondingWith(201, CREATED));

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getContentAsString()).isEqualTo(CREATED);
        assertThat(executions).hasValue(1);
        assertThat(transactionManager.outcomes()).containsExactly("commit");
        IdempotencyRecord saved = store.stored(KEY).orElseThrow();
        assertThat(saved.userId()).isEqualTo(USER);
        assertThat(saved.responseStatus()).isEqualTo(201);
        assertThat(saved.responseBody()).isEqualTo(CREATED);
    }

    @Test
    void repetirLaMismaPeticionDevuelveLaMismaRespuestaSinEjecutarNada() throws Exception {
        send("POST", "/batches", KEY, USER, "{\"kg\":10}", respondingWith(201, CREATED));

        MockHttpServletResponse replay = send("POST", "/batches", KEY, USER, "{\"kg\":10}", respondingWith(201, "{\"id\":\"otro\"}"));

        assertThat(executions).hasValue(1);
        assertThat(replay.getStatus()).isEqualTo(201);
        assertThat(replay.getContentAsString()).isEqualTo(CREATED);
        assertThat(replay.getContentType()).startsWith("application/json");
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    void elCuerpoSePuedeReleerEnElRestoDeLaCadena() throws Exception {
        String[] seen = new String[1];
        FilterChain chain = (req, res) -> {
            seen[0] = new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            res.getOutputStream().write(CREATED.getBytes(StandardCharsets.UTF_8));
            res.setContentType("application/json");
        };

        send("POST", "/batches", KEY, USER, "{\"kg\":10}", chain);

        assertThat(seen[0]).isEqualTo("{\"kg\":10}");
    }

    @Test
    void respuestaSinCuerpoSeReplicaSinCuerpo() throws Exception {
        send("PUT", "/batches/b-1", KEY, USER, "{}", (req, res) -> {
            executions.incrementAndGet();
            ((HttpServletResponse) res).setStatus(204);
        });

        MockHttpServletResponse replay = send("PUT", "/batches/b-1", KEY, USER, "{}", respondingWith(200, "{}"));

        assertThat(executions).hasValue(1);
        assertThat(replay.getStatus()).isEqualTo(204);
        assertThat(replay.getContentAsByteArray()).isEmpty();
    }

    @Test
    void soSeGuardaElCuerpoDeRespuestasJson() throws Exception {
        FilterChain texto = (req, res) -> {
            executions.incrementAndGet();
            res.setContentType("text/plain");
            res.getOutputStream().write("hola".getBytes(StandardCharsets.UTF_8));
        };

        MockHttpServletResponse first = send("POST", "/batches", KEY, USER, "{}", texto);
        MockHttpServletResponse replay = send("POST", "/batches", KEY, USER, "{}", texto);

        assertThat(first.getContentAsString()).isEqualTo("hola");
        assertThat(store.stored(KEY).orElseThrow().responseBody()).isNull();
        assertThat(executions).hasValue(1);
        assertThat(replay.getContentAsByteArray()).isEmpty();
    }

    // --- Claves reutilizadas con otra petición ---------------------------------------------------------------------------------

    @Test
    void mismaClaveConOtroCuerpoDevuelve422YConservaLaRespuestaOriginal() throws Exception {
        send("POST", "/batches", KEY, USER, "{\"kg\":10}", respondingWith(201, CREATED));

        MockHttpServletResponse conflict = send("POST", "/batches", KEY, USER, "{\"kg\":99}", respondingWith(201, "{\"id\":\"b-2\"}"));

        assertThat(conflict.getStatus()).isEqualTo(422);
        assertThat(errorCode(conflict)).isEqualTo(IdempotencyFilter.IDEMPOTENCY_KEY_REUSED);
        assertThat(executions).hasValue(1);
        assertThat(store.stored(KEY).orElseThrow().responseBody()).isEqualTo(CREATED);
    }

    @Test
    void mismaClaveEnOtraRutaDevuelve422() throws Exception {
        send("POST", "/batches", KEY, USER, "{}", respondingWith(201, CREATED));

        MockHttpServletResponse conflict = send("POST", "/transfers", KEY, USER, "{}", respondingWith(201, CREATED));

        assertThat(conflict.getStatus()).isEqualTo(422);
        assertThat(executions).hasValue(1);
    }

    @Test
    void mismaClaveDeOtroUsuarioDevuelve422SinFiltrarLaRespuestaAjena() throws Exception {
        send("POST", "/batches", KEY, USER, "{}", respondingWith(201, CREATED));

        MockHttpServletResponse conflict = send("POST", "/batches", KEY, "otro-usuario", "{}", respondingWith(201, CREATED));

        assertThat(conflict.getStatus()).isEqualTo(422);
        assertThat(conflict.getContentAsString()).doesNotContain("b-1");
        assertThat(executions).hasValue(1);
    }

    // --- Rollback por código de estado y por excepción ---------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(ints = {302, 400, 404, 409, 422, 500})
    void unaRespuestaNo2xxLlegaAlClienteRevierteYNoGuardaLaClave(int status) throws Exception {
        MockHttpServletResponse response = send("POST", "/batches", KEY, USER, "{}", respondingWith(status, "{\"code\":\"X\"}"));

        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(response.getContentAsString()).isEqualTo("{\"code\":\"X\"}");
        assertThat(transactionManager.outcomes()).containsExactly("rollback");
        assertThat(store.size()).isZero();

        MockHttpServletResponse retry = send("POST", "/batches", KEY, USER, "{}", respondingWith(201, CREATED));

        assertThat(retry.getStatus()).isEqualTo(201);
        assertThat(executions).hasValue(2);
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    void unaExcepcionRevierteNoGuardaLaClaveYSePropaga() {
        FilterChain failing = (req, res) -> {
            executions.incrementAndGet();
            throw new IllegalStateException("boom");
        };

        assertThatThrownBy(() -> send("POST", "/batches", KEY, USER, "{}", failing))
                .isInstanceOf(IllegalStateException.class).hasMessage("boom");

        assertThat(transactionManager.outcomes()).containsExactly("rollback");
        assertThat(store.size()).isZero();
    }

    @Test
    void lasExcepcionesComprobadasDeLaCadenaSePropaganTalCualYRevierten() {
        FilterChain servletFailure = (req, res) -> {
            throw new ServletException("servlet");
        };
        FilterChain ioFailure = (req, res) -> {
            throw new IOException("io");
        };

        assertThatThrownBy(() -> send("POST", "/batches", KEY, USER, "{}", servletFailure))
                .isInstanceOf(ServletException.class).hasMessage("servlet");
        assertThatThrownBy(() -> send("POST", "/batches", KEY, USER, "{}", ioFailure))
                .isInstanceOf(IOException.class).hasMessage("io");

        assertThat(transactionManager.outcomes()).containsExactly("rollback", "rollback");
        assertThat(store.size()).isZero();
    }

    @Test
    void unParticipanteQueMarcaRollbackOnlyConRespuesta2xxDevuelve500YNoGuardaLaClave() throws Exception {
        FilterChain useCaseSwallowingFailure = (req, res) -> {
            executions.incrementAndGet();
            new TransactionTemplate(transactionManager).execute(tx -> {
                tx.setRollbackOnly(); // el caso de uso se une a la transacción del filtro y la marca rollback-only
                return null;
            });
            res.setContentType("application/json");
            res.getOutputStream().write(CREATED.getBytes(StandardCharsets.UTF_8));
            ((HttpServletResponse) res).setStatus(201);
        };

        MockHttpServletResponse response = send("POST", "/batches", KEY, USER, "{}", useCaseSwallowingFailure);

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(errorCode(response)).isEqualTo(IdempotencyFilter.OPERATION_ROLLED_BACK);
        assertThat(response.getContentAsString()).doesNotContain("b-1");
        assertThat(transactionManager.outcomes()).containsExactly("rollback");
        assertThat(store.size()).isZero();

        MockHttpServletResponse retry = send("POST", "/batches", KEY, USER, "{}", respondingWith(201, CREATED));

        assertThat(retry.getStatus()).isEqualTo(201);
        assertThat(executions).hasValue(2);
    }

    // --- Caducidad ---------------------------------------------------------------------------------

    @Test
    void antesDe24HorasSeRepiteLaRespuestaYDespuesSeEjecutaDeNuevo() throws Exception {
        send("POST", "/batches", KEY, USER, "{}", respondingWith(201, CREATED));

        clock.advance(Duration.ofHours(23));
        send("POST", "/batches", KEY, USER, "{}", respondingWith(201, "{\"id\":\"b-2\"}"));
        assertThat(executions).hasValue(1);

        clock.advance(Duration.ofHours(2));
        MockHttpServletResponse afterExpiry = send("POST", "/batches", KEY, USER, "{}", respondingWith(201, "{\"id\":\"b-2\"}"));

        assertThat(executions).hasValue(2);
        assertThat(afterExpiry.getContentAsString()).isEqualTo("{\"id\":\"b-2\"}");
        assertThat(store.stored(KEY).orElseThrow().responseBody()).isEqualTo("{\"id\":\"b-2\"}");
    }

    // --- Concurrencia: la PK serializa las peticiones con la misma clave ---------------------------------------------------------------------------------

    @Test
    void dosPeticionesSimultaneasConLaMismaClaveEjecutanUnaSolaVez() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        FilterChain slow = (req, res) -> {
            executions.incrementAndGet();
            awaitQuietly(release);
            ((HttpServletResponse) res).setStatus(201);
            res.setContentType("application/json");
            res.getOutputStream().write(CREATED.getBytes(StandardCharsets.UTF_8));
        };

        Call first = start(slow);
        awaitStarted(executions, 1);
        Call second = start(respondingWith(201, "{\"id\":\"duplicado\"}"));
        awaitBlocked(second.thread());
        assertThat(second.response()).isNotDone();

        release.countDown();

        assertThat(first.response().get(10, TimeUnit.SECONDS).getContentAsString()).isEqualTo(CREATED);
        MockHttpServletResponse replay = second.response().get(10, TimeUnit.SECONDS);
        assertThat(replay.getStatus()).isEqualTo(201);
        assertThat(replay.getContentAsString()).isEqualTo(CREATED);
        assertThat(executions).hasValue(1);
        assertThat(transactionManager.outcomes()).containsExactly("commit", "commit");
    }

    @Test
    void siLaPrimeraPeticionFallaLaSimultaneaSeEjecuta() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        FilterChain failing = (req, res) -> {
            executions.incrementAndGet();
            awaitQuietly(release);
            ((HttpServletResponse) res).setStatus(409);
            res.setContentType("application/json");
            res.getOutputStream().write("{\"code\":\"X\"}".getBytes(StandardCharsets.UTF_8));
        };

        Call first = start(failing);
        awaitStarted(executions, 1);
        Call second = start(respondingWith(201, CREATED));
        awaitBlocked(second.thread());

        release.countDown();

        assertThat(first.response().get(10, TimeUnit.SECONDS).getStatus()).isEqualTo(409);
        MockHttpServletResponse ok = second.response().get(10, TimeUnit.SECONDS);
        assertThat(ok.getStatus()).isEqualTo(201);
        assertThat(executions).hasValue(2);
        assertThat(transactionManager.outcomes()).containsExactly("rollback", "commit");
        assertThat(store.size()).isEqualTo(1);
    }

    // --- Ayudas ---------------------------------------------------------------------------------

    // --- multipart: el hash es de las partes, no del cuerpo crudo --------------------------------------------------

    private static MockHttpServletRequest multipart(String uri, String boundary, String key, String user, Part... parts) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setContentType("multipart/form-data; boundary=" + boundary);
        // el cuerpo crudo cambia con el separador; las partes no
        request.setContent(("--" + boundary + "--").getBytes(StandardCharsets.UTF_8));
        request.addHeader("Idempotency-Key", key);
        request.addHeader("X-User-Id", user);
        for (Part part : parts) {
            request.addPart(part);
        }
        return request;
    }

    private static MockPart photo(String field, String fileName, String type, String content) {
        MockPart part = new MockPart(field, fileName, content.getBytes(StandardCharsets.UTF_8));
        part.getHeaders().setContentType(org.springframework.http.MediaType.parseMediaType(type));
        return part;
    }

    @Test
    void multipartConLosMismosArchivosYOtroSeparadorEsLaMismaPeticion() throws Exception {
        filter.doFilter(multipart("/batches/1/evidence", "----AAA", KEY, USER, photo("files", "a.jpg", "image/jpeg", "uno"),
                photo("files", "b.png", "image/png", "dos")), new MockHttpServletResponse(), respondingWith(201, CREATED));
        MockHttpServletResponse retry = new MockHttpServletResponse();

        filter.doFilter(multipart("/batches/1/evidence", "----BBB-otro-separador", KEY, USER, photo("files", "a.jpg", "image/jpeg", "uno"),
                photo("files", "b.png", "image/png", "dos")), retry, respondingWith(201, "{\"no\":\"debe ejecutarse\"}"));

        assertThat(retry.getStatus()).isEqualTo(201);
        assertThat(retry.getContentAsString()).as("la respuesta guardada").isEqualTo(CREATED);
        assertThat(executions).hasValue(1);
    }

    @Test
    void multipartConOtroArchivoOtroNombreOtroTipoOOtroCampoEs422() throws Exception {
        filter.doFilter(multipart("/batches/1/evidence", "A", KEY, USER, photo("files", "a.jpg", "image/jpeg", "uno")),
                new MockHttpServletResponse(), respondingWith(201, CREATED));

        for (Part different : new Part[]{photo("files", "a.jpg", "image/jpeg", "uno!"), photo("files", "otro.jpg", "image/jpeg", "uno"),
                photo("files", "a.jpg", "image/png", "uno"), photo("fotos", "a.jpg", "image/jpeg", "uno")}) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(multipart("/batches/1/evidence", "A", KEY, USER, different), response, respondingWith(201, CREATED));
            assertThat(response.getStatus()).as(different.getName() + " " + different.getSubmittedFileName()).isEqualTo(422);
            assertThat(response.getContentAsString()).contains("IDEMPOTENCY_KEY_REUSED");
        }
        assertThat(executions).hasValue(1);
    }

    @Test
    void multipartConUnArchivoDeMasOEnOtroOrdenEs422YOtraRutaTambien() throws Exception {
        filter.doFilter(multipart("/batches/1/evidence", "A", KEY, USER, photo("files", "a.jpg", "image/jpeg", "uno"),
                photo("files", "b.jpg", "image/jpeg", "dos")), new MockHttpServletResponse(), respondingWith(201, CREATED));

        MockHttpServletResponse reordered = new MockHttpServletResponse();
        filter.doFilter(multipart("/batches/1/evidence", "A", KEY, USER, photo("files", "b.jpg", "image/jpeg", "dos"),
                photo("files", "a.jpg", "image/jpeg", "uno")), reordered, respondingWith(201, CREATED));
        MockHttpServletResponse extra = new MockHttpServletResponse();
        filter.doFilter(multipart("/batches/1/evidence", "A", KEY, USER, photo("files", "a.jpg", "image/jpeg", "uno"),
                photo("files", "b.jpg", "image/jpeg", "dos"), photo("files", "c.jpg", "image/jpeg", "tres")), extra, respondingWith(201, CREATED));
        MockHttpServletResponse otherRoute = new MockHttpServletResponse();
        filter.doFilter(multipart("/batches/2/evidence", "A", KEY, USER, photo("files", "a.jpg", "image/jpeg", "uno"),
                photo("files", "b.jpg", "image/jpeg", "dos")), otherRoute, respondingWith(201, CREATED));

        assertThat(reordered.getStatus()).isEqualTo(422);
        assertThat(extra.getStatus()).isEqualTo(422);
        assertThat(otherRoute.getStatus()).isEqualTo(422);
    }

    @Test
    void elContenidoDeUnaParteNoSePuedeConfundirConElNombreDeLaSiguiente() throws Exception {
        // "ab" + "c" frente a "a" + "bc": sin longitudes por delante darían el mismo hash
        filter.doFilter(multipart("/x", "A", KEY, USER, photo("ab", "f", "text/plain", "c")), new MockHttpServletResponse(), respondingWith(201, CREATED));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(multipart("/x", "A", KEY, USER, photo("a", "bf", "text/plain", "c")), response, respondingWith(201, CREATED));

        assertThat(response.getStatus()).isEqualTo(422);
    }

    @Test
    void multipartNoLeeElCuerpoCrudoYElControladorRecibeLaPeticionOriginalConSusPartes() throws Exception {
        MockHttpServletRequest request = multipart("/batches/1/evidence", "A", KEY, USER, photo("files", "a.jpg", "image/jpeg", "uno"));
        AtomicInteger partsSeenByTheController = new AtomicInteger();

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            partsSeenByTheController.set(((jakarta.servlet.http.HttpServletRequest) req).getParts().size());
            ((HttpServletResponse) res).setStatus(201);
        });

        assertThat(partsSeenByTheController).hasValue(1);
        assertThat(request.getContentAsByteArray()).as("el cuerpo no se consumió").isNotEmpty();
    }

    @Test
    void siElContenedorNoPuedeLeerLasPartesLaPeticionSigueSinIdempotenciaParaQueElControladorResponda() throws Exception {
        MockHttpServletRequest broken = new MockHttpServletRequest("POST", "/batches/1/evidence") {
            @Override
            public java.util.Collection<jakarta.servlet.http.Part> getParts() {
                throw new IllegalStateException("tamaño máximo excedido");
            }
        };
        broken.setContentType("multipart/form-data; boundary=A");
        broken.addHeader("Idempotency-Key", KEY);
        broken.addHeader("X-User-Id", USER);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(broken, response, respondingWith(413, "{\"code\":\"FILE_TOO_LARGE\"}"));

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(executions).hasValue(1);
        assertThat(store.size()).as("un rechazo nunca deja la clave").isZero();
        assertThat(transactionManager.outcomes()).isEmpty();
    }

    private FilterChain respondingWith(int status, String body) {
        return (req, res) -> {
            executions.incrementAndGet();
            ((HttpServletResponse) res).setStatus(status);
            res.setContentType("application/json");
            res.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        };
    }

    private static MockHttpServletRequest request(String method, String uri, String key, String user, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        if (key != null) {
            request.addHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key);
        }
        if (user != null) {
            request.addHeader(IdempotencyFilter.USER_ID_HEADER, user);
        }
        return request;
    }

    private MockHttpServletResponse send(String method, String uri, String key, String user, String body, FilterChain chain)
            throws ServletException, IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request(method, uri, key, user, body), response, chain);
        return response;
    }

    private String errorCode(MockHttpServletResponse response) throws Exception {
        return mapper.readTree(response.getContentAsString()).path("code").asString();
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void awaitStarted(AtomicInteger counter, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (counter.get() < expected) {
            assertThat(System.nanoTime()).isLessThan(deadline);
            Thread.sleep(5);
        }
    }

    private record Call(Thread thread, CompletableFuture<MockHttpServletResponse> response) {
    }

    /** Lanza la petición en su propio hilo para poder observar si queda bloqueada esperando la clave. */
    private Call start(FilterChain chain) {
        CompletableFuture<MockHttpServletResponse> response = new CompletableFuture<>();
        Thread thread = new Thread(() -> {
            try {
                response.complete(send("POST", "/batches", KEY, USER, "{}", chain));
            } catch (Throwable t) {
                response.completeExceptionally(t);
            }
        });
        thread.setDaemon(true);
        thread.start();
        return new Call(thread, response);
    }

    private static void awaitBlocked(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (thread.getState() != Thread.State.WAITING && thread.getState() != Thread.State.TIMED_WAITING) {
            assertThat(System.nanoTime()).isLessThan(deadline);
            Thread.sleep(5);
        }
    }
}
