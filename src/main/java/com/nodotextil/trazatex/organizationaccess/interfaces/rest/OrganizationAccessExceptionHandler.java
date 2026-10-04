package com.nodotextil.trazatex.organizationaccess.interfaces.rest;

import com.nodotextil.trazatex.organizationaccess.domain.ExternalServiceUnavailableException;
import com.nodotextil.trazatex.organizationaccess.domain.InvalidCredentialsException;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationAccessDeniedException;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationConflictException;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationNotFoundException;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationValidationException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


@RestControllerAdvice(basePackages = "com.nodotextil.trazatex.organizationaccess.interfaces.rest")
public class OrganizationAccessExceptionHandler {

	private static final Logger log = LoggerFactory
			.getLogger(OrganizationAccessExceptionHandler.class);

	@ExceptionHandler(OrganizationValidationException.class)
	ResponseEntity<ErrorResponse> handleValidation(OrganizationValidationException exception) {
		return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", exception.getMessage(),
				exception.fieldErrors());
	}

	@ExceptionHandler(OrganizationNotFoundException.class)
	ResponseEntity<ErrorResponse> handleNotFound(OrganizationNotFoundException exception) {
		return respond(HttpStatus.NOT_FOUND, "NOT_FOUND", exception.getMessage(), Map.of());
	}

	@ExceptionHandler(OrganizationConflictException.class)
	ResponseEntity<ErrorResponse> handleConflict(OrganizationConflictException exception) {
		return respond(HttpStatus.CONFLICT, "CONFLICT", exception.getMessage(), Map.of());
	}

	@ExceptionHandler(InvalidCredentialsException.class)
	ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException exception) {
		return respond(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", exception.getMessage(), Map.of());
	}

	@ExceptionHandler(OrganizationAccessDeniedException.class)
	ResponseEntity<ErrorResponse> handleAccessDenied(OrganizationAccessDeniedException exception) {
		return respond(HttpStatus.FORBIDDEN, "FORBIDDEN", exception.getMessage(), Map.of());
	}

	@ExceptionHandler(ExternalServiceUnavailableException.class)
	ResponseEntity<ErrorResponse> handleExternalService(
			ExternalServiceUnavailableException exception) {
		log.warn("External service unavailable: {}", exception.getMessage());
		return respond(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
				exception.getMessage(), Map.of());
	}

	private static ResponseEntity<ErrorResponse> respond(HttpStatus status, String code,
			String message, Map<String, String> fieldErrors) {
		return ResponseEntity.status(status).body(new ErrorResponse(code, message, fieldErrors));
	}

	public record ErrorResponse(String code, String message, Map<String, String> fieldErrors) {
	}
}
