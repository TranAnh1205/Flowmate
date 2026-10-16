package com.smarttask.gateway.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/system")
public class AdminSystemController {
    private final WebClient client;
    private final Map<String, String> services;

    public AdminSystemController(
            WebClient.Builder builder,
            @Value("${admin.services.auth:${AUTH_SERVICE_URL:http://localhost:8081}}") String auth,
            @Value("${admin.services.task:${TASK_SERVICE_URL:http://localhost:8082}}") String task,
            @Value("${admin.services.ai:${AI_SERVICE_URL:http://localhost:8083}}") String ai,
            @Value("${admin.services.notification:${NOTIFICATION_SERVICE_URL:http://localhost:8084}}") String notification) {
        this.client = builder.build();
        this.services = Map.of(
                "authService", auth,
                "taskService", task,
                "aiService", ai,
                "notificationService", notification);
    }

    @GetMapping("/health")
    public Mono<Map<String, Object>> health() {
        Map<String, Mono<ServiceHealth>> checks = new LinkedHashMap<>();
        services.forEach((name, url) -> checks.put(name, check(url)));
        return Mono.zip(checks.values(), values -> {
            Map<String, Object> response = new LinkedHashMap<>();
            boolean allUp = true;
            int index = 0;
            for (String name : checks.keySet()) {
                ServiceHealth value = (ServiceHealth) values[index++];
                response.put(name, value);
                allUp &= "UP".equals(value.status());
            }
            response.put("status", allUp ? "UP" : "DEGRADED");
            response.put("checkedAt", Instant.now());
            return response;
        });
    }

    private Mono<ServiceHealth> check(String baseUrl) {
        long start = System.nanoTime();
        return client.get()
                .uri(baseUrl + "/actuator/health")
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(3))
                .map(body -> new ServiceHealth(
                        "UP",
                        Math.max(0, (System.nanoTime() - start) / 1_000_000),
                        Instant.now()))
                .onErrorReturn(new ServiceHealth(
                        "DOWN",
                        Math.max(0, (System.nanoTime() - start) / 1_000_000),
                        Instant.now()));
    }

    public record ServiceHealth(String status, long responseTimeMs, Instant checkedAt) {}
}
