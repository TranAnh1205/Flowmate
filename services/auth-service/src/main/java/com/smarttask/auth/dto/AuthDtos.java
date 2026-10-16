package com.smarttask.auth.dto;

import com.smarttask.auth.domain.AccountStatus;
import com.smarttask.auth.domain.UserRole;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 72) String password) {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
    public record RefreshRequest(@NotBlank String refreshToken) {}
    public record LogoutRequest(@NotBlank String refreshToken) {}
    public record ChangePasswordRequest(@NotBlank String currentPassword,
                                        @NotBlank @Size(min = 8, max = 72) String newPassword) {}
    public record ReauthenticateRequest(@NotBlank String password) {}
    public record ReauthenticateResponse(String adminActionToken, long expiresIn) {}

    public record UserResponse(UUID id, String fullName, String email, String avatarUrl, String timezone,
                               UserRole role, AccountStatus status, Instant createdAt, Instant updatedAt) {}
    public record AuthResponse(String accessToken, String refreshToken, String tokenType,
                               long expiresIn, UserResponse user) {}
    public record UpdateProfileRequest(@NotBlank @Size(max = 120) String fullName,
                                       @Size(max = 500) String avatarUrl,
                                       @NotBlank @Size(max = 80) String timezone) {}
    public record SettingsResponse(LocalTime workStartTime, LocalTime workEndTime,
                                   int defaultReminderMinutes, int weekStartDay,
                                   String language, String theme) {}
    public record UpdateSettingsRequest(@NotNull LocalTime workStartTime,
                                        @NotNull LocalTime workEndTime,
                                        @Min(0) @Max(10080) int defaultReminderMinutes,
                                        @Min(1) @Max(7) int weekStartDay,
                                        @NotBlank String language,
                                        @Pattern(regexp = "light|dark|system") String theme) {}
}
