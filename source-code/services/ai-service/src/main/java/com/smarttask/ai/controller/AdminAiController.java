package com.smarttask.ai.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/admin/ai")
public class AdminAiController {
    private final boolean enabled;
    private final String model;
    private final String baseUrl;

    public AdminAiController(
            @Value("${app.ai.enabled:false}") boolean enabled,
            @Value("${spring.ai.openai.chat.options.model:not-configured}") String model,
            @Value("${spring.ai.openai.base-url:not-configured}") String baseUrl) {
        this.enabled = enabled;
        this.model = model;
        this.baseUrl = baseUrl;
    }

    @GetMapping("/status")
    @PreAuthorize("hasAuthority('AI_MONITOR_VIEW')")
    public AiStatus status() {
        return new AiStatus(enabled, enabled ? "EXTERNAL_PROVIDER" : "LOCAL_FALLBACK",
                model, sanitizeProvider(baseUrl), Instant.now());
    }

    private String sanitizeProvider(String value) {
        if (value == null || value.isBlank() || "not-configured".equals(value)) return "not-configured";
        try {
            java.net.URI uri = java.net.URI.create(value);
            return uri.getScheme() + "://" + uri.getHost();
        } catch (Exception ex) {
            return "configured";
        }
    }

    public record AiStatus(boolean enabled, String mode, String model,
                           String provider, Instant checkedAt) {}
}
