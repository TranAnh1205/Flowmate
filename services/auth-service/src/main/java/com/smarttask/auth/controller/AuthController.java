package com.smarttask.auth.controller;

import com.smarttask.auth.dto.AuthDtos.*;
import com.smarttask.auth.dto.AdminDtos.RequestMetadata;
import com.smarttask.auth.service.AuthService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService service;
    public AuthController(AuthService service) { this.service = service; }

    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) { return service.register(request); }
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return service.login(request, metadata(http));
    }
    @PostMapping("/refresh-token")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest http) {
        return service.refresh(request, metadata(http));
    }
    @PostMapping("/logout")
    public Map<String, String> logout(@Valid @RequestBody LogoutRequest request) {
        service.logout(request); return Map.of("message", "Đăng xuất thành công");
    }
    @PostMapping("/change-password")
    public Map<String, String> changePassword(Authentication authentication, @Valid @RequestBody ChangePasswordRequest request) {
        service.changePassword(UUID.fromString(authentication.getName()), request);
        return Map.of("message", "Đổi mật khẩu thành công");
    }

    @PostMapping("/reauthenticate")
    public ReauthenticateResponse reauthenticate(Authentication authentication,
                                                 @Valid @RequestBody ReauthenticateRequest request) {
        return service.reauthenticate(UUID.fromString(authentication.getName()), request);
    }

    private RequestMetadata metadata(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded == null || forwarded.isBlank()
                ? request.getRemoteAddr()
                : forwarded.split(",")[0].trim();
        String userAgent = truncate(request.getHeader("User-Agent"), 500);
        String platform = truncate(request.getHeader("Sec-CH-UA-Platform"), 80);
        String device = platform == null || platform.isBlank() ? inferDevice(userAgent) : platform.replace("\"", "");
        return new RequestMetadata(truncate(ip, 64), userAgent, truncate(device, 160));
    }

    private String inferDevice(String userAgent) {
        if (userAgent == null) return "Thiết bị không xác định";
        if (userAgent.contains("Mobile")) return "Thiết bị di động";
        if (userAgent.contains("Windows")) return "Windows";
        if (userAgent.contains("Macintosh")) return "macOS";
        if (userAgent.contains("Linux")) return "Linux";
        return "Trình duyệt web";
    }

    private String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
