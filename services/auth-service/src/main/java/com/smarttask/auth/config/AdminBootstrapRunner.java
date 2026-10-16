package com.smarttask.auth.config;

import com.smarttask.auth.domain.*;
import com.smarttask.auth.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalTime;
import java.util.Locale;

@Component
public class AdminBootstrapRunner implements ApplicationRunner {
    private final UserRepository users;
    private final UserSettingsRepository settings;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;
    private final String email;
    private final String password;
    private final String fullName;

    public AdminBootstrapRunner(
            UserRepository users,
            UserSettingsRepository settings,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin-bootstrap.enabled:false}") boolean enabled,
            @Value("${app.admin-bootstrap.email:}") String email,
            @Value("${app.admin-bootstrap.password:}") String password,
            @Value("${app.admin-bootstrap.full-name:Flowmate Administrator}") String fullName) {
        this.users = users;
        this.settings = settings;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
        this.email = email;
        this.password = password;
        this.fullName = fullName;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) return;
        if (email.isBlank() || password.length() < 12) {
            throw new IllegalStateException(
                    "Admin bootstrap yêu cầu email và mật khẩu tối thiểu 12 ký tự");
        }
        if (users.countByRole(UserRole.ADMIN) > 0) return;

        User admin = users.findByEmailIgnoreCase(email.trim()).orElseGet(User::new);
        boolean isNew = admin.getId() == null;
        admin.setFullName(fullName.trim());
        admin.setEmail(email.trim().toLowerCase(Locale.ROOT));
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setTimezone("Asia/Bangkok");
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(AccountStatus.ACTIVE);
        admin.setStatusReason("Tài khoản quản trị khởi tạo");
        admin = users.save(admin);

        if (isNew) {
            UserSettings userSettings = new UserSettings();
            userSettings.setUserId(admin.getId());
            userSettings.setWorkStartTime(LocalTime.of(8, 0));
            userSettings.setWorkEndTime(LocalTime.of(17, 30));
            userSettings.setDefaultReminderMinutes(30);
            userSettings.setWeekStartDay(1);
            userSettings.setLanguage("vi");
            userSettings.setTheme("system");
            settings.save(userSettings);
        }
    }
}
