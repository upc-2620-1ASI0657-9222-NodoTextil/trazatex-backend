package com.nodotextil.trazatex.organizationaccess.infrastructure.external;

import com.nodotextil.trazatex.organizationaccess.application.port.out.PasswordBreachCheckerPort;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class HibpPasswordBreachCheckerAdapter implements PasswordBreachCheckerPort {

    private static final Logger log = LoggerFactory.getLogger(HibpPasswordBreachCheckerAdapter.class);

    private final RestClient client;
    private final CircuitBreaker breaker;
    private final boolean enabled;

    public HibpPasswordBreachCheckerAdapter(RestClient client, CircuitBreaker breaker, boolean enabled) {
        this.client = client;
        this.breaker = breaker;
        this.enabled = enabled;
    }

    @Override
    public Result check(String rawPassword) {
        if (!enabled) {
            return Result.CLEAN;
        }
        String sha1 = sha1Hex(rawPassword);
        String prefix = sha1.substring(0, 5);
        String suffix = sha1.substring(5);
        try {
            String ranges = breaker.executeSupplier(() -> fetchRange(prefix));
            return containsLeaked(ranges, suffix) ? Result.COMPROMISED : Result.CLEAN;
        } catch (HibpUnavailableException | CallNotPermittedException e) {
            log.warn("Have I Been Pwned no respondió ({}): se acepta la contraseña y se audita", e.getMessage());
            return Result.UNAVAILABLE;
        }
    }

    private String fetchRange(String prefix) {
        try {
            return client.get()
                    .uri("/range/{prefix}", prefix)
                    .header("Add-Padding", "true")
                    .header("User-Agent", "TrazaTex-organization-access")
                    .accept(MediaType.TEXT_PLAIN)
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status != 200) {
                            throw new HibpUnavailableException("HTTP " + status);
                        }
                        return new String(response.getBody().readNBytes(2 * 1024 * 1024), StandardCharsets.UTF_8);
                    });
        } catch (RestClientException e) {
            throw new HibpUnavailableException(e.getClass().getSimpleName());
        }
    }

    private static boolean containsLeaked(String ranges, String suffix) {
        for (String line : ranges.split("\\r?\\n")) {
            int colon = line.indexOf(':');
            if (colon > 0 && line.substring(0, colon).trim().equalsIgnoreCase(suffix)) {
                try {
                    return Long.parseLong(line.substring(colon + 1).trim()) > 0;
                } catch (NumberFormatException malformed) {
                    throw new HibpUnavailableException("línea con formato inesperado");
                }
            }
        }
        return false;
    }

    private static String sha1Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().withUpperCase().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class HibpUnavailableException extends RuntimeException {

        HibpUnavailableException(String message) {
            super(message, null, false, false);
        }
    }
}
