package com.smarttask.task.service;

import com.smarttask.task.domain.ReminderSyncCommand;
import com.smarttask.task.repository.ReminderSyncCommandRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReminderSyncQueueServiceTest {
    private final ReminderSyncCommandRepository repository = mock(ReminderSyncCommandRepository.class);
    private final ReminderSyncQueueService queue = new ReminderSyncQueueService(repository);

    @Test
    void replacesPendingCommandForSameTask() {
        UUID userId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        ReminderSyncCommand existing = new ReminderSyncCommand();
        existing.setUserId(userId);
        existing.setTaskId(taskId);
        existing.setAction("DELETE");
        when(repository.findByTaskId(taskId)).thenReturn(Optional.of(existing));
        Instant scheduledAt = Instant.now().plusSeconds(300);

        queue.enqueueUpsert(userId, taskId, scheduledAt);

        assertEquals("UPSERT", existing.getAction());
        assertEquals(scheduledAt, existing.getScheduledAt());
        assertEquals(0, existing.getAttempts());
        assertNull(existing.getLastError());
        verify(repository).save(existing);
    }

    @Test
    void createsDeleteCommandWhenNoPendingCommandExists() {
        UUID userId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        when(repository.findByTaskId(taskId)).thenReturn(Optional.empty());

        queue.enqueueDelete(userId, taskId);

        verify(repository).save(any(ReminderSyncCommand.class));
    }
}
