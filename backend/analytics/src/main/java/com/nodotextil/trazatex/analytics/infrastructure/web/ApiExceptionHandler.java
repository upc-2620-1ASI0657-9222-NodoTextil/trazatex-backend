package com.nodotextil.trazatex.analytics.infrastructure.web;

import com.nodotextil.trazatex.sharedkernel.error.ApiError;
import com.nodotextil.trazatex.sharedkernel.exception.ForbiddenException;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Todo error HTTP de Analytics es un {@link ApiError} con un código del contrato (api.md). */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(AuthenticationRequiredException.class)
    ResponseEntity<ApiError> unauthenticated(AuthenticationRequiredException e) { return respond(HttpStatus.UNAUTHORIZED, ApiError.of(e.code(), e.getMessage())); }

    @ExceptionHandler(ForbiddenException.class)
    ResponseEntity<ApiError> forbidden(ForbiddenException e) { return respond(HttpStatus.FORBIDDEN, ApiError.of(e.code(), e.getMessage())); }

    @ExceptionHandler(ValidationException.class)
    ResponseEntity<ApiError> invalid(ValidationException e) { return respond(HttpStatus.BAD_REQUEST, ApiError.of(e.code(), e.getMessage(), e.details())); }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception e) {
        log.error("Error inesperado en Analytics", e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, ApiError.of("INTERNAL_ERROR", "Ocurrió un error inesperado. Intenta de nuevo."));
    }

    /** Errores estándar de Spring MVC (método no permitido, ruta inexistente...): su estado, con el mismo cuerpo {@link ApiError}. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        String code = switch (statusCode.value()) {
            case 400 -> "MALFORMED_REQUEST";
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            default -> "ERROR";
        };
        String message = switch (statusCode.value()) {
            case 400 -> "La petición está mal formada.";
            case 404 -> "El recurso no existe.";
            case 405 -> "Método no permitido.";
            default -> "Error en la petición.";
        };
        HttpHeaders response = new HttpHeaders();
        response.putAll(headers);
        response.set("Cache-Control", "no-store");
        return new ResponseEntity<>(ApiError.of(code, message), response, statusCode);
    }

    private static ResponseEntity<ApiError> respond(HttpStatus status, ApiError body) {
        return ResponseEntity.status(status).header("Cache-Control", "no-store").body(body);
    }
}
