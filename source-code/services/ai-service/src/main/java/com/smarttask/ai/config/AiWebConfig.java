package com.smarttask.ai.config;

import com.smarttask.ai.ratelimit.AiRateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AiWebConfig implements WebMvcConfigurer {
    private final AiRateLimitInterceptor rateLimitInterceptor;

    public AiWebConfig(AiRateLimitInterceptor rateLimitInterceptor) {
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/api/v1/ai/**");
    }
}
