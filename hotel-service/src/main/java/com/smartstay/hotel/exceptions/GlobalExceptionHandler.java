package com.smartstay.hotel.exceptions;

import java.time.OffsetDateTime;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.smartstay.hotel.dto.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 404 — hotel or room not found
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex,
                                                        HttpServletRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    // 409 — duplicate resource (e.g., room number already exists in hotel)
    @ExceptionHandler(ResourceExistsException.class)
    public ResponseEntity<ErrorResponse> handleResourceExists(ResourceExistsException ex,
                                                              HttpServletRequest request) {
        log.warn("Resource conflict: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    // 409 — MongoDB unique-index violation (e.g., hotel with same name+city already exists for this manager)
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateKey(DuplicateKeyException ex,
                                                            HttpServletRequest request) {
        log.warn("Duplicate key violation at {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.CONFLICT,
                "A hotel with this name already exists in the same city for your account", request);
    }

    // 403 — caller is not the hotel owner
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException ex,
                                                         HttpServletRequest request) {
        // Not logged here — already logged at the source in HotelServiceImpl
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    // 400 — @Valid / @NotBlank / @Positive constraint violations
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest request) {
        String message = ex.getBindingResult()
                           .getFieldErrors()
                           .stream()
                           .map(err -> err.getField() + ": " + err.getDefaultMessage())
                           .collect(Collectors.joining(", "));
        log.warn("Validation failed: {}", message);
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    // 400 — missing required @RequestParam
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex,
                                                            HttpServletRequest request) {
        String message = "Required parameter '" + ex.getParameterName() + "' is missing";
        log.warn("Missing request parameter: {}", message);
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    // 400 — invalid enum value or malformed JSON (e.g. type: "INVALID")
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException ex,
                                                           HttpServletRequest request) {
        log.warn("Unreadable request body: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "Invalid request body — check field types and values", request);
    }

    // 403 — @PreAuthorize / @PostAuthorize denial (Spring Security method security)
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex,
                                                            HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "Access denied", request);
    }

    // 500 — catch-all for anything unexpected
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex,
                                                       HttpServletRequest request) {
        log.error("Unexpected error at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
    }

    // -------------------------------------------------------------------------

    private ResponseEntity<ErrorResponse> build(HttpStatus status,
                                                String message,
                                                HttpServletRequest request) {
        return ResponseEntity.status(status)
                             .body(new ErrorResponse(OffsetDateTime.now(), status.value(), status.getReasonPhrase(),
                                     message, request.getRequestURI()));
    }
}
