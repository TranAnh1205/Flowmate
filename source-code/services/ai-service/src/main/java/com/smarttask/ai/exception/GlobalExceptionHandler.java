package com.smarttask.ai.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.*;
import com.smarttask.ai.ratelimit.AiRateLimitExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    public record ErrorResponse(Instant timestamp, int status, String error, String code,
                                String message, String path, Map<String, String> details) {}

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, String> details = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> details.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Dữ liệu đầu vào không hợp lệ",
                request.getRequestURI(), details);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ErrorResponse> badInput(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "Tham số hoặc dữ liệu đầu vào không hợp lệ", request.getRequestURI(), Map.of());
    }

    @ExceptionHandler(AiRateLimitExceededException.class)
    ResponseEntity<ErrorResponse> rateLimit(AiRateLimitExceededException exception,
                                            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(exception.getRetryAfterSeconds()))
                .body(new ErrorResponse(Instant.now(), HttpStatus.TOO_MANY_REQUESTS.value(),
                        HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(), "AI_RATE_LIMIT_EXCEEDED",
                        exception.getMessage(), request.getRequestURI(),
                        Map.of("retryAfterSeconds", String.valueOf(exception.getRetryAfterSeconds()))));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
                "Không thể xử lý yêu cầu AI lúc này", request.getRequestURI(), Map.of());
    }

    private ResponseEntity<ErrorResponse> response(HttpStatus status, String code, String message,
                                                    String path, Map<String, String> details) {
        return ResponseEntity.status(status).body(new ErrorResponse(Instant.now(), status.value(),
                status.getReasonPhrase(), code, message, path, details));
    }
}
