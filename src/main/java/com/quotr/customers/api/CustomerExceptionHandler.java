package com.quotr.customers.api;

import com.quotr.customers.api.model.ErrorResponse;
import com.quotr.customers.domain.CustomerNotFoundException;
import com.quotr.customers.domain.CustomerValidationException;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class CustomerExceptionHandler {

    @ExceptionHandler(CustomerNotFoundException.class)
    ResponseEntity<ErrorResponse> notFound(CustomerNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(error("CUSTOMER_NOT_FOUND", exception.getMessage(), List.of()));
    }

    @ExceptionHandler(CustomerValidationException.class)
    ResponseEntity<ErrorResponse> validation(CustomerValidationException exception) {
        return ResponseEntity.badRequest()
                .body(error("CUSTOMER_VALIDATION_FAILED", exception.getMessage(), List.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> beanValidation(MethodArgumentNotValidException exception) {
        List<String> details = exception.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .toList();
        return ResponseEntity.badRequest()
                .body(error("CUSTOMER_VALIDATION_FAILED", "Customer request validation failed", details));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ErrorResponse> constraintViolation(ConstraintViolationException exception) {
        List<String> details = exception.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .toList();
        return ResponseEntity.badRequest()
                .body(error("CUSTOMER_VALIDATION_FAILED", "Customer request validation failed", details));
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }

    private static ErrorResponse error(String code, String message, List<String> details) {
        ErrorResponse response = new ErrorResponse();
        response.setCode(code);
        response.setMessage(message);
        response.setDetails(details);
        return response;
    }
}
