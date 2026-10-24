package com.paisafollows.common;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Errors our own code throws on purpose. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, String>> handleApi(ApiException e) {
        return reply(e.code(), e.getMessage());
    }

    /** Request fields that break a @Valid rule (bad email, PAN format, ...). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return reply(ErrorCode.INVALID_INPUT, message);
    }

    /** Body is not valid JSON, or a value has the wrong type (e.g. a bad date). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadable(HttpMessageNotReadableException e) {
        return reply(ErrorCode.INVALID_INPUT, "Request body is not valid JSON or has a wrongly formatted value");
    }

    private ResponseEntity<Map<String, String>> reply(ErrorCode code, String message) {
        return ResponseEntity.status(code.status())
                .body(Map.of("error", code.name(), "message", message));
    }
}