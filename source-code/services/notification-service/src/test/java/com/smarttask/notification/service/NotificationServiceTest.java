package com.smarttask.notification.service;

import com.smarttask.notification.domain.Notification;
import com.smarttask.notification.domain.Reminder;
import com.smarttask.notification.dto.NotificationDtos.ReminderRequest;
import com.smarttask.notification.repository.NotificationRepository;
import com.smarttask.notification.repository.ReminderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
    @Mock ReminderRepository reminders;
    @Mock NotificationRepository notifications;
    NotificationService service;

    @BeforeEach
    void setUp() { service = new NotificationService(reminders, notifications); }

    @Test
    void createReminderBelongsToAuthenticatedUser() {
        UUID userId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        when(reminders.save(any(Reminder.class))).thenAnswer(invocation -> {
            Reminder reminder = invocation.getArgument(0);
            ReflectionTestUtils.invokeMethod(reminder, "pre");
            return reminder;
        });

        var response = service.createReminder(userId,
                new ReminderRequest(taskId, Instant.now().plusSeconds(60), "DEADLINE"));

        assertEquals(taskId, response.taskId());
        assertEquals("PENDING", response.status());
        verify(reminders).save(argThat(value -> value.getUserId().equals(userId)));
    }

    @Test
    void dueReminderCreatesInAppNotificationExactlyOnce() {
        Reminder reminder = new Reminder();
        ReflectionTestUtils.invokeMethod(reminder, "pre");
        reminder.setUserId(UUID.randomUUID());
        reminder.setTaskId(UUID.randomUUID());
        reminder.setScheduledAt(Instant.now().minusSeconds(1));
        reminder.setType("DEADLINE");
        when(reminders.findTop100ByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(eq("PENDING"), any()))
                .thenReturn(List.of(reminder));

        service.processDue();

        assertEquals("PROCESSED", reminder.getStatus());
        assertNotNull(reminder.getProcessedAt());
        verify(notifications, times(1)).save(any(Notification.class));
    }

    @Test
    void userCannotReadAnotherUsersNotification() {
        UUID notificationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(notifications.findByIdAndUserId(notificationId, userId)).thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.read(userId, notificationId));

        assertEquals(404, error.getStatusCode().value());
    }

    @Test
    void listRejectsInvalidPaginationBeforeQueryingDatabase() {
        ResponseStatusException negativePage = assertThrows(
                ResponseStatusException.class,
                () -> service.list(UUID.randomUUID(), -1, 20));
        ResponseStatusException emptyPage = assertThrows(
                ResponseStatusException.class,
                () -> service.list(UUID.randomUUID(), 0, 0));

        assertEquals(400, negativePage.getStatusCode().value());
        assertEquals(400, emptyPage.getStatusCode().value());
        verifyNoInteractions(notifications);
    }
}
