package com.smarttask.auth.controller;

import com.smarttask.auth.dto.AuthDtos.*;
import com.smarttask.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {
    private final AuthService service;
    public UserController(AuthService service) { this.service = service; }

    @GetMapping
    public UserResponse profile(Authentication authentication) { return service.profile(userId(authentication)); }
    @PutMapping
    public UserResponse updateProfile(Authentication authentication, @Valid @RequestBody UpdateProfileRequest request) {
        return service.updateProfile(userId(authentication), request);
    }
    @GetMapping("/settings")
    public SettingsResponse settings(Authentication authentication) { return service.getSettings(userId(authentication)); }
    @PutMapping("/settings")
    public SettingsResponse updateSettings(Authentication authentication, @Valid @RequestBody UpdateSettingsRequest request) {
        return service.updateSettings(userId(authentication), request);
    }
    private UUID userId(Authentication authentication) { return UUID.fromString(authentication.getName()); }
}

