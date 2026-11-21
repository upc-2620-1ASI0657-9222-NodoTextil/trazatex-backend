package com.nodotextil.trazatex.apigateway.infrastructure.web;

import java.time.Instant;
import java.util.List;

record GatewayError(String code, String message, List<String> details, Instant timestamp) {

    static GatewayError of(String code, String message) {
        return new GatewayError(code, message, List.of(), Instant.now());
    }
}
