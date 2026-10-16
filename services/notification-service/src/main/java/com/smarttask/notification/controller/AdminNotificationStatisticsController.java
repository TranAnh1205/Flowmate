package com.smarttask.notification.controller;

import com.smarttask.notification.repository.NotificationRepository;
import com.smarttask.notification.repository.ReminderRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.*;

@RestController
@RequestMapping("/api/v1/admin/statistics/notifications")
public class AdminNotificationStatisticsController {
    private final NotificationRepository notifications;
    private final ReminderRepository reminders;

    public AdminNotificationStatisticsController(
            NotificationRepository notifications,
            ReminderRepository reminders) {
        this.notifications = notifications;
        this.reminders = reminders;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_HEALTH_VIEW')")
    public NotificationSystemStatistics statistics() {
        Instant today = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        return new NotificationSystemStatistics(
                notifications.count(),
                notifications.countByReadFalse(),
                notifications.countByCreatedAtGreaterThanEqual(today),
                reminders.countByStatus("PENDING"),
                reminders.countByStatus("PROCESSING"),
                reminders.countByStatus("PROCESSED"),
                reminders.countByStatusAndScheduledAtBefore("PENDING", Instant.now()));
    }

    public record NotificationSystemStatistics(
            long totalNotifications,
            long unreadNotifications,
            long createdToday,
            long pendingReminders,
            long processingReminders,
            long processedReminders,
            long overduePendingReminders
    ) {}
}
