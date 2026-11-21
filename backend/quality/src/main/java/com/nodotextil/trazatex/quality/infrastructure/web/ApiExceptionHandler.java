package com.nodotextil.trazatex.quality.infrastructure.web;

import com.nodotextil.trazatex.quality.application.exception.PdfGenerationException;
import com.nodotextil.trazatex.quality.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.quality.domain.exception.FileTooLargeException;
import com.nodotextil.trazatex.quality.infrastructure.web.security.AuthenticationRequiredException;
import com.nodotextil.trazatex.sharedkernel.error.ApiError;
import com.nodotextil.trazatex.sharedkernel.exception.ConflictException;
import com.nodotextil.trazatex.sharedkernel.exception.ForbiddenException;
import com.nodotextil.trazatex.sharedkernel.exception.NotFoundException;
import com.nodotextil.trazatex.sharedkernel.exception.TrazatexException;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(TrazatexException.class)
    ResponseEntity<ApiError> handleBusiness(TrazatexException e) {
        return respond(statusOf(e), ApiError.of(e.code(), e.getMessage(), e.details()));
    }

    @ExceptionHandler(PdfGenerationException.class)
    ResponseEntity<ApiError> handlePdf(PdfGenerationException e) {
        log.error("No se pudo generar el reporte", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header(HttpHeaders.RETRY_AFTER, "5")
                .body(ApiError.of("REPORT_UNAVAILABLE", "No se pudo generar el reporte en este momento. Intenta de nuevo en unos segundos."));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return respond(HttpStatus.BAD_REQUEST, ApiError.of("INVALID_PARAMETER",
                "El valor del parámetro '" + e.getName() + "' no es válido.", List.of(e.getName())));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException e) {
        log.warn("Violación de integridad: {}", e.getMostSpecificCause().getMessage());
        return respond(HttpStatus.CONFLICT, ApiError.of("CONFLICT", "La operación choca con datos existentes."));
    }

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex, HttpHeaders headers,
                                                                          HttpStatusCode status, WebRequest request) {
        return new ResponseEntity<>(ApiError.of(ErrorCodes.FILE_TOO_LARGE,
                "Cada fotografía puede pesar como máximo 10 MB (y una petición, 5 fotografías)."), headers, HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestPart(MissingServletRequestPartException ex, HttpHeaders headers,
                                                                     HttpStatusCode status, WebRequest request) {
        return new ResponseEntity<>(ApiError.of(ErrorCodes.NO_FILES, "Adjunte al menos una fotografía (campo \"files\")."), headers, status);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception e) {
        log.error("Error no controlado", e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, ApiError.of("INTERNAL_ERROR", "Ocurrió un error inesperado."));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode,
                                                             WebRequest request) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        String code = switch (statusCode.value()) {
            case 400 -> "MALFORMED_REQUEST";
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            default -> "ERROR";
        };
        String message = status == null ? "Error en la petición." : switch (statusCode.value()) {
            case 400 -> "La petición está mal formada.";
            case 404 -> "El recurso no existe.";
            case 405 -> "Método no permitido.";
            case 415 -> "Tipo de contenido no soportado.";
            default -> status.getReasonPhrase();
        };
        return new ResponseEntity<>(ApiError.of(code, message), headers, statusCode);
    }

    private static HttpStatus statusOf(TrazatexException e) {
        return switch (e) {
            case AuthenticationRequiredException x -> HttpStatus.UNAUTHORIZED;
            case FileTooLargeException x -> HttpStatus.PAYLOAD_TOO_LARGE;
            case ForbiddenException x -> HttpStatus.FORBIDDEN;
            case NotFoundException x -> HttpStatus.NOT_FOUND;
            case ConflictException x -> HttpStatus.CONFLICT;
            case ValidationException x -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.BAD_REQUEST;
        };
    }

    private static ResponseEntity<ApiError> respond(HttpStatus status, ApiError body) {
        return ResponseEntity.status(status).body(body);
    }
}
