package com.nodotextil.trazatex.sharedkernel.error;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record ApiError(String code, String message, List<String> details, Instant timestamp) {

    public ApiError {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        details = details == null ? List.of() : List.copyOf(details);
        timestamp = timestamp == null ? Instant.now() : timestamp;
    }

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, List.of(), Instant.now());
    }

    public static ApiError of(String code, String message, List<String> details) {
        return new ApiError(code, message, details, Instant.now());
    }
}
