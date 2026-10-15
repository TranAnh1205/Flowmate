package com.smarttask.task.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class ReminderGateway {
    private final RestClient client;
    private final String internalServiceKey;

    public ReminderGateway(RestClient.Builder builder,
                           @Value("${app.services.notification-url:http://localhost:8084}") String notificationUrl,
                           @Value("${app.internal-service-key:}") String internalServiceKey) {
        this.client = builder.baseUrl(notificationUrl).build();
        this.internalServiceKey = internalServiceKey;
    }

    public void upsert(UUID userId, UUID taskId, Instant scheduledAt) {
        requireConfiguredKey();
        client.put()
                .uri("/internal/v1/reminders/task/{taskId}", taskId)
                .header("X-Internal-Service-Key", internalServiceKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("userId", userId, "scheduledAt", scheduledAt, "type", "DEADLINE"))
                .retrieve().toBodilessEntity();
    }

    public void delete(UUID userId, UUID taskId) {
        requireConfiguredKey();
        client.delete()
                .uri(uriBuilder -> uriBuilder
                        .path("/internal/v1/reminders/task/{taskId}")
                        .queryParam("userId", userId)
                        .build(taskId))
                .header("X-Internal-Service-Key", internalServiceKey)
                .retrieve().toBodilessEntity();
    }

    private void requireConfiguredKey() {
        if (internalServiceKey == null || internalServiceKey.length() < 32) {
            throw new IllegalStateException("INTERNAL_SERVICE_KEY phải có ít nhất 32 ký tự");
        }
    }
}
