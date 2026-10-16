package com.smarttask.auth.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "user_settings")
public class UserSettings {
    @Id
    private UUID id;
    @Column(nullable = false, unique = true)
    private UUID userId;
    @Column(nullable = false)
    private LocalTime workStartTime;
    @Column(nullable = false)
    private LocalTime workEndTime;
    @Column(nullable = false)
    private int defaultReminderMinutes;
    @Column(nullable = false)
    private int weekStartDay;
    @Column(nullable = false, length = 10)
    private String language;
    @Column(nullable = false, length = 20)
    private String theme;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public LocalTime getWorkStartTime() { return workStartTime; }
    public void setWorkStartTime(LocalTime workStartTime) { this.workStartTime = workStartTime; }
    public LocalTime getWorkEndTime() { return workEndTime; }
    public void setWorkEndTime(LocalTime workEndTime) { this.workEndTime = workEndTime; }
    public int getDefaultReminderMinutes() { return defaultReminderMinutes; }
    public void setDefaultReminderMinutes(int value) { this.defaultReminderMinutes = value; }
    public int getWeekStartDay() { return weekStartDay; }
    public void setWeekStartDay(int weekStartDay) { this.weekStartDay = weekStartDay; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }
}

