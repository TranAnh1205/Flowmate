package com.smarttask.auth.service;

import com.smarttask.auth.domain.*;
import com.smarttask.auth.dto.AuthDtos.ChangePasswordRequest;
import com.smarttask.auth.dto.AuthDtos.LoginRequest;
import com.smarttask.auth.dto.AuthDtos.RegisterRequest;
import com.smarttask.auth.exception.ApiException;
import com.smarttask.auth.repository.RefreshTokenRepository;
import com.smarttask.auth.repository.UserRepository;
import com.smarttask.auth.repository.UserSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock UserRepository users;
    @Mock RefreshTokenRepository refreshTokens;
    @Mock UserSettingsRepository settings;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock AdminActionTokenService adminActionTokens;
    @Mock AdminAuditService audit;
    TokenHashService hashing = new TokenHashService();
    AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, refreshTokens, settings, passwordEncoder, jwtService,
                hashing, adminActionTokens, audit, Duration.ofDays(30));
        lenient().when(jwtService.createAccessToken(any(User.class))).thenReturn("access-token");
        lenient().when(jwtService.expiresInSeconds()).thenReturn(900L);
    }

    @Test
    void registerNormalizesEmailHashesPasswordAndCreatesSettings() {
        when(users.existsByEmailIgnoreCase("user@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123")).thenReturn("bcrypt-hash");
        when(users.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });

        var response = service.register(new RegisterRequest("  Nguyễn Văn A  ", "  USER@Example.com ", "Password123"));

        assertEquals("user@example.com", response.user().email());
        assertEquals("Nguyễn Văn A", response.user().fullName());
        assertEquals(UserRole.USER, response.user().role());
        assertEquals(AccountStatus.ACTIVE, response.user().status());
        assertEquals("access-token", response.accessToken());
        assertNotNull(response.refreshToken());
        verify(passwordEncoder).encode("Password123");
        verify(settings).save(any());
        verify(refreshTokens).save(any());
    }

    @Test
    void registerNormalizesEmailIndependentlyOfServerLocale() {
        when(users.existsByEmailIgnoreCase(any())).thenReturn(false);
        when(users.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });

        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            var response = service.register(new RegisterRequest("User", "INVITE@example.com", "Password123"));
            assertEquals("invite@example.com", response.user().email());
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void registerRejectsExistingEmail() {
        when(users.existsByEmailIgnoreCase("user@example.com")).thenReturn(true);

        ApiException error = assertThrows(ApiException.class,
                () -> service.register(new RegisterRequest("User", "user@example.com", "Password123")));

        assertEquals("EMAIL_ALREADY_EXISTS", error.getCode());
        verify(users, never()).save(any());
    }

    @Test
    void loginIssuesTokensForValidCredentials() {
        User user = activeUser();
        when(users.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123", "bcrypt-hash")).thenReturn(true);

        var response = service.login(new LoginRequest("user@example.com", "Password123"));

        assertEquals(user.getId(), response.user().id());
        assertEquals("access-token", response.accessToken());
        verify(refreshTokens).save(any());
    }

    @Test
    void loginRejectsWrongPassword() {
        User user = activeUser();
        when(users.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "bcrypt-hash")).thenReturn(false);

        ApiException error = assertThrows(ApiException.class,
                () -> service.login(new LoginRequest("user@example.com", "wrong")));

        assertEquals("INVALID_CREDENTIALS", error.getCode());
        verify(refreshTokens, never()).save(any());
    }

    @Test
    void changePasswordEncodesNewPasswordAndRevokesActiveSessions() {
        User user = activeUser();
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123", "bcrypt-hash")).thenReturn(true);
        when(passwordEncoder.encode("NewPassword123")).thenReturn("new-bcrypt-hash");

        service.changePassword(
                user.getId(),
                new ChangePasswordRequest("Password123", "NewPassword123"));

        assertEquals("new-bcrypt-hash", user.getPasswordHash());
        verify(refreshTokens).revokeActiveTokensByUserId(eq(user.getId()), any(), eq("Đổi mật khẩu"));
        verify(adminActionTokens).revokeAll(user.getId());
    }

    @Test
    void changePasswordRejectsWrongCurrentPasswordWithoutRevokingSessions() {
        User user = activeUser();
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "bcrypt-hash")).thenReturn(false);

        ApiException error = assertThrows(
                ApiException.class,
                () -> service.changePassword(
                        user.getId(),
                        new ChangePasswordRequest("wrong-password", "NewPassword123")));

        assertEquals("INVALID_CREDENTIALS", error.getCode());
        verify(passwordEncoder, never()).encode(any());
        verify(refreshTokens, never()).revokeActiveTokensByUserId(any(), any(), any());
    }

    private User activeUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFullName("User");
        user.setEmail("user@example.com");
        user.setPasswordHash("bcrypt-hash");
        user.setTimezone("Asia/Bangkok");
        user.setRole(UserRole.USER);
        user.setStatus(AccountStatus.ACTIVE);
        return user;
    }
}
