package com.localserve.web;

import com.localserve.shared.error.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ApiProblem> domain(DomainException error, HttpServletRequest request) {
        HttpStatus status = statusFor(error.code());
        return problem(status, error.code(), error.getMessage(), request, error.safeMetadata());
    }

    private static HttpStatus statusFor(String code) {
        if (code.equals("ACCESS.DENIED") || code.endsWith(".ACCESS_DENIED")) return HttpStatus.FORBIDDEN;
        if (code.equals("AUTH.INVALID_CREDENTIALS") || code.equals("WEBHOOK.SIGNATURE_INVALID")) return HttpStatus.UNAUTHORIZED;
        if (code.endsWith(".NOT_FOUND")) return HttpStatus.NOT_FOUND;
        if (code.startsWith("AUTH.OTP_") || code.startsWith("REQUEST.")) return HttpStatus.BAD_REQUEST;
        return HttpStatus.CONFLICT;
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class, IllegalArgumentException.class})
    ResponseEntity<ApiProblem> validation(Exception error, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "REQUEST.VALIDATION_FAILED", "Request validation failed", request, Map.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiProblem> unexpected(Exception error, HttpServletRequest request) {
        log.error("Unhandled request failure", error);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "SYSTEM.INTERNAL_ERROR", "An unexpected error occurred", request, Map.of());
    }

    private static ResponseEntity<ApiProblem> problem(HttpStatus status, String code, String detail,
                                                       HttpServletRequest request, Map<String, Object> metadata) {
        String correlationId = MDC.get("correlationId");
        return ResponseEntity.status(status).body(new ApiProblem("https://api.localserve.example/problems/" + code.toLowerCase().replace('.', '-'),
                status.getReasonPhrase(), status.value(), code, detail, request.getRequestURI(),
                correlationId, Instant.now(), metadata));
    }
}
