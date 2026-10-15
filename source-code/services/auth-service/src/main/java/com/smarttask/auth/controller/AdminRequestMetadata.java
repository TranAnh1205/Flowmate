package com.smarttask.auth.controller;

import com.smarttask.auth.dto.AdminDtos.RequestMetadata;
import jakarta.servlet.http.HttpServletRequest;

final class AdminRequestMetadata {
    private AdminRequestMetadata() {}

    static RequestMetadata from(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded == null || forwarded.isBlank()
                ? request.getRemoteAddr()
                : forwarded.split(",")[0].trim();
        return new RequestMetadata(truncate(ip, 64),
                truncate(request.getHeader("User-Agent"), 500), null);
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
