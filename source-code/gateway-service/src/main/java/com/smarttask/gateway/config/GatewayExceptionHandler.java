package com.smarttask.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.time.Instant;
import java.util.Map;

@Component
@Order(-2)
public class GatewayExceptionHandler implements WebExceptionHandler {
    private final ObjectMapper objectMapper;

    public GatewayExceptionHandler(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable exception) {
        if (exchange.getResponse().isCommitted()) return Mono.error(exception);
        HttpStatus status;
        String code;
        String message;
        if (exception instanceof ResponseStatusException responseStatus) {
            status = HttpStatus.valueOf(responseStatus.getStatusCode().value());
            code = status == HttpStatus.NOT_FOUND ? "RESOURCE_NOT_FOUND" : "REQUEST_FAILED";
            message = responseStatus.getReason() == null ? status.getReasonPhrase() : responseStatus.getReason();
        } else if (hasConnectCause(exception)) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            code = "SERVICE_UNAVAILABLE";
            message = "Dịch vụ tạm thời không khả dụng";
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            code = "INTERNAL_SERVER_ERROR";
            message = "Gateway không thể xử lý yêu cầu";
        }
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] body = objectMapper.writeValueAsBytes(Map.of(
                    "timestamp", Instant.now().toString(),
                    "status", status.value(),
                    "error", status.getReasonPhrase(),
                    "code", code,
                    "message", message,
                    "path", exchange.getRequest().getPath().value()));
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
        } catch (Exception serializationError) {
            return exchange.getResponse().setComplete();
        }
    }

    private boolean hasConnectCause(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ConnectException) return true;
            current = current.getCause();
        }
        return false;
    }
}
