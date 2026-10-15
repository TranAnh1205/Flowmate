package com.smarttask.gateway.config;

import java.net.InetSocketAddress;
import java.security.Principal;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimitConfig {

    @Bean("principalOrIpKeyResolver")
    @Primary
    KeyResolver principalOrIpKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .map(Principal::getName)
                .map(name -> "user:" + name)
                .switchIfEmpty(Mono.fromSupplier(() -> "ip:" + remoteAddress(exchange)));
    }

    @Bean("ipKeyResolver")
    KeyResolver ipKeyResolver() {
        return exchange -> Mono.just("ip:" + remoteAddress(exchange));
    }

    private String remoteAddress(ServerWebExchange exchange) {
        InetSocketAddress address = exchange.getRequest().getRemoteAddress();
        return address == null || address.getAddress() == null
                ? "unknown"
                : address.getAddress().getHostAddress();
    }
}
