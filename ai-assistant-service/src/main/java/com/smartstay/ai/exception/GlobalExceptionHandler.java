package com.smartstay.ai.exception;

import java.time.OffsetDateTime;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 400 — @Valid failures on request DTOs
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(final MethodArgumentNotValidException ex,
                                                                final WebRequest request) {

        final String message = ex.getBindingResult()
                                 .getFieldErrors()
                                 .stream()
                                 .map(e -> e.getField() + ": " + e.getDefaultMessage())
                                 .findFirst()
                                 .orElse("Validation failed");

        log.warn("Validation error: {}", message);
        return ResponseEntity.badRequest()
                             .body(errorBody(400, "Bad Request", message, request));
    }

    // 400 — explicit illegal argument (e.g. blank sessionId passed programmatically)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(final IllegalArgumentException ex,
                                                                     final WebRequest request) {

        log.warn("Illegal argument: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                             .body(errorBody(400, "Bad Request", ex.getMessage(), request));
    }

    // 500 — catch-all
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(final Exception ex,
                                                             final WebRequest request) {

        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                             .body(errorBody(500, "Internal Server Error",
                                     "An unexpected error occurred. Please try again later.", request));
    }

    private Map<String, Object> errorBody(final int status,
                                          final String error,
                                          final String message,
                                          final WebRequest request) {
        return Map.of("timestamp", OffsetDateTime.now()
                                                 .toString(), "status", status, "error", error, "message", message,
                "path", request.getDescription(false)
                               .replace("uri=", ""));
    }
}
