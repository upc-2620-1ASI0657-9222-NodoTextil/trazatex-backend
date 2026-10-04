package com.nodotextil.trazatex.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void notFoundExceptionReturns404WithNotFoundCode() {
        ResponseEntity<ApiError> response = handler.handleNotFound(new DomainNotFoundException("Batch not found"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(response.getBody().message()).isEqualTo("Batch not found");
    }

    @Test
    void validationExceptionReturns400WithValidationCode() {
        ResponseEntity<ApiError> response = handler.handleValidation(new DomainValidationException("Invalid state"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void accessDeniedReturns403WithForbiddenCode() {
        ResponseEntity<ApiError> response = handler.handleForbidden(new AccessDeniedException("Not allowed"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.FORBIDDEN);
    }
}
