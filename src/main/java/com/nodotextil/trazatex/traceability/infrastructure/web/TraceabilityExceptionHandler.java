package com.nodotextil.trazatex.traceability.infrastructure.web;

import com.nodotextil.trazatex.traceability.application.LotNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = TraceabilityController.class)
public class TraceabilityExceptionHandler {

    record ApiError(String code, String message) {
    }

    @ExceptionHandler(LotNotFoundException.class)
    ResponseEntity<ApiError> handleNotFound(LotNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("BATCH_NOT_FOUND", exception.getMessage()));
    }
}