package com.nodotextil.trazatex.production.infrastructure.web;

import com.nodotextil.trazatex.production.application.BatchNotFoundException;
import com.nodotextil.trazatex.production.application.MachineNotFoundException;
import com.nodotextil.trazatex.production.application.TransformationNotFoundException;
import com.nodotextil.trazatex.production.application.TransferNotFoundException;
import com.nodotextil.trazatex.production.domain.InvalidBatchException;
import com.nodotextil.trazatex.production.domain.InvalidMachineException;
import com.nodotextil.trazatex.production.domain.InvalidTransformationException;
import com.nodotextil.trazatex.production.domain.InvalidTransferException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = {
    BatchController.class,
    MachineController.class,
    TransformationController.class,
    TransferController.class
})
public class ProductionExceptionHandler {

    @ExceptionHandler(BatchNotFoundException.class)
    ResponseEntity<ApiError> handleBatchNotFound(BatchNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("BATCH_NOT_FOUND", exception.getMessage(), Map.of()));
    }

    @ExceptionHandler(MachineNotFoundException.class)
    ResponseEntity<ApiError> handleMachineNotFound(MachineNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("MACHINE_NOT_FOUND", exception.getMessage(), Map.of()));
    }

    @ExceptionHandler(TransformationNotFoundException.class)
    ResponseEntity<ApiError> handleTransformationNotFound(
            TransformationNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(
                        "TRANSFORMATION_NOT_FOUND", exception.getMessage(), Map.of()));
    }

    @ExceptionHandler(TransferNotFoundException.class)
    ResponseEntity<ApiError> handleTransferNotFound(TransferNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("TRANSFER_NOT_FOUND", exception.getMessage(), Map.of()));
    }

    @ExceptionHandler({
        InvalidBatchException.class,
        InvalidMachineException.class,
        InvalidTransformationException.class,
        InvalidTransferException.class
    })
    ResponseEntity<ApiError> handleDomainValidation(RuntimeException exception) {
        return ResponseEntity.badRequest()
                .body(new ApiError("VALIDATION_ERROR", exception.getMessage(), Map.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleRequestValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return ResponseEntity.badRequest()
                .body(new ApiError("VALIDATION_ERROR", "Request validation failed", fieldErrors));
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<ApiError> handleMalformedRequest(Exception exception) {
        return ResponseEntity.badRequest()
                .body(new ApiError("VALIDATION_ERROR", "Malformed request", Map.of()));
    }

    public record ApiError(String code, String message, Map<String, String> fieldErrors) {
    }
}
