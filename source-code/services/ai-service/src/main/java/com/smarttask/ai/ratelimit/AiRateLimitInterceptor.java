package com.smarttask.ai.ratelimit;

import java.security.Principal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AiRateLimitInterceptor implements HandlerInterceptor {
    private static final String PERMIT_ATTRIBUTE = AiRateLimitInterceptor.class.getName() + ".permit";
    private final AiRateLimitService rateLimitService;

    public AiRateLimitInterceptor(AiRateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Principal principal = request.getUserPrincipal();
        String key = principal == null ? "ip:" + request.getRemoteAddr() : "user:" + principal.getName();
        AiRateLimitService.Permit permit = rateLimitService.acquire(key, requestCost(request.getRequestURI()));
        request.setAttribute(PERMIT_ATTRIBUTE, permit);
        response.setHeader("X-RateLimit-Limit", String.valueOf(rateLimitService.minuteLimit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(permit.minuteRemaining()));
        response.setHeader("X-AI-Daily-Limit", String.valueOf(rateLimitService.dailyLimit()));
        response.setHeader("X-AI-Daily-Remaining", String.valueOf(permit.dailyRemaining()));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception exception) {
        rateLimitService.release((AiRateLimitService.Permit) request.getAttribute(PERMIT_ATTRIBUTE));
    }

    private int requestCost(String uri) {
        return uri.endsWith("/breakdown-task") || uri.endsWith("/daily-plan") || uri.endsWith("/weekly-summary")
                ? 2 : 1;
    }
}
