package com.smarttask.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorsConfigTest {
    @Test
    void acceptsIpv4LocalhostAndAdminActionHeader() {
        String origin = "http://127.0.0.1:3000";
        var source = new CorsConfig().corsConfigurationSource(List.of(
                "http://localhost:*", "http://127.0.0.1:*"));
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
                .options("/api/v1/admin/users/example/status")
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-Admin-Action-Token, Content-Type"));
        CorsConfiguration config = source.getCorsConfiguration(exchange);

        assertNotNull(config);
        assertEquals(origin, config.checkOrigin(origin));
        assertTrue(config.getAllowedMethods().contains(HttpMethod.PATCH.name()));
        assertTrue(config.getAllowedHeaders().contains("X-Admin-Action-Token"));
        assertNull(config.checkOrigin("https://untrusted.example"));
    }
}
