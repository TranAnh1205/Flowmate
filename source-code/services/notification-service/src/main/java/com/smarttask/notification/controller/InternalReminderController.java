package com.smarttask.notification.controller;

import com.smarttask.notification.dto.NotificationDtos.InternalReminderSyncRequest;
import com.smarttask.notification.dto.NotificationDtos.ReminderResponse;
import com.smarttask.notification.dto.NotificationDtos.ReminderSyncRequest;
import com.smarttask.notification.service.InternalServiceKeyVerifier;
import com.smarttask.notification.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/reminders")
public class InternalReminderController {
    private final NotificationService notifications;
    private final InternalServiceKeyVerifier keys;

    public InternalReminderController(NotificationService notifications, InternalServiceKeyVerifier keys) {
        this.notifications = notifications;
        this.keys = keys;
    }

    @PutMapping("/task/{taskId}")
    public ReminderResponse sync(@RequestHeader("X-Internal-Service-Key") String key,
                                 @PathVariable UUID taskId,
                                 @Valid @RequestBody InternalReminderSyncRequest request) {
        keys.verify(key);
        return notifications.syncReminder(request.userId(), taskId,
                new ReminderSyncRequest(request.scheduledAt(), request.type()));
    }

    @DeleteMapping("/task/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader("X-Internal-Service-Key") String key,
                       @PathVariable UUID taskId,
                       @RequestParam UUID userId) {
        keys.verify(key);
        notifications.deleteTaskReminders(userId, taskId);
    }
}
