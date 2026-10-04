package com.nodotextil.trazatex.shared.exception;

import java.util.Map;

public record ApiError(ErrorCode code, String message, Map<String, String> fieldErrors) {
    public ApiError(ErrorCode code, String message) {
        this(code, message, Map.of());
    }
}
