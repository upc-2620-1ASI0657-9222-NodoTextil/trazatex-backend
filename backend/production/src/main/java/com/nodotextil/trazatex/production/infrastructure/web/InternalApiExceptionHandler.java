package com.nodotextil.trazatex.production.infrastructure.web;

import com.nodotextil.trazatex.production.application.exception.TemporarilyUnavailableException;
import com.nodotextil.trazatex.production.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.error.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Errores del comando interno que difieren de {@code /api}: una carrera nunca es 409 (Quality cancela el control con un 409) sino
 * 503 con {@code Retry-After}, que Quality reintenta con el mismo {@code requestId}. Va antes que {@link ApiExceptionHandler},
 * que para {@code /api} sí responde 409 {@code CONCURRENT_MODIFICATION}; todo lo demás (rechazos de negocio, 400, 404) lo resuelve
 * ese handler.
 */
@RestControllerAdvice(assignableTypes = InternalBatchController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class InternalApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(InternalApiExceptionHandler.class);

    @ExceptionHandler(TemporarilyUnavailableException.class)
    ResponseEntity<ApiError> handleUnavailable(TemporarilyUnavailableException e) {
        return unavailable(e.getMessage());
    }

    /** Red de seguridad: si una carrera se escapara del servicio sin convertir, sigue siendo "reintente", nunca 409 ni 500. */
    @ExceptionHandler({ConcurrencyFailureException.class, DataIntegrityViolationException.class})
    ResponseEntity<ApiError> handleRace(RuntimeException e) {
        log.warn("Carrera no controlada en el comando interno: {}", e.toString());
        return unavailable("El lote cambió al mismo tiempo: repita el comando.");
    }

    private static ResponseEntity<ApiError> unavailable(String message) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header(HttpHeaders.RETRY_AFTER, "1")
                .body(ApiError.of(ErrorCodes.TEMPORARILY_UNAVAILABLE, message));
    }
}
