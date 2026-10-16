package com.smarttask.task.service;

import com.smarttask.task.domain.ReminderSyncCommand;
import com.smarttask.task.repository.ReminderSyncCommandRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReminderSyncWorkerTest {
    private final ReminderSyncCommandRepository repository = mock(ReminderSyncCommandRepository.class);
    private final ReminderGateway gateway = mock(ReminderGateway.class);
    private final ReminderSyncWorker worker = new ReminderSyncWorker(repository, gateway);

    @Test
    void deletesCommandAfterSuccessfulDelivery() {
        ReminderSyncCommand command = command("UPSERT");
        when(repository.findById(command.getId())).thenReturn(Optional.of(command));

        worker.deliver(command.getId());

        verify(gateway).upsert(command.getUserId(), command.getTaskId(), command.getScheduledAt());
        verify(repository).delete(command);
    }

    @Test
    void keepsCommandAndSchedulesRetryAfterFailure() {
        ReminderSyncCommand command = command("DELETE");
        when(repository.findById(command.getId())).thenReturn(Optional.of(command));
        doThrow(new IllegalStateException("notification unavailable"))
                .when(gateway).delete(command.getUserId(), command.getTaskId());

        worker.deliver(command.getId());

        assertEquals(1, command.getAttempts());
        assertNotNull(command.getNextAttemptAt());
        assertEquals("notification unavailable", command.getLastError());
        verify(repository, never()).delete(command);
    }

    private ReminderSyncCommand command(String action) {
        ReminderSyncCommand command = new ReminderSyncCommand();
        command.setUserId(UUID.randomUUID());
        command.setTaskId(UUID.randomUUID());
        command.setAction(action);
        command.setScheduledAt(Instant.now().plusSeconds(300));
        command.setNextAttemptAt(Instant.now());
        try {
            var method = ReminderSyncCommand.class.getDeclaredMethod("prePersist");
            method.setAccessible(true);
            method.invoke(command);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
        return command;
    }
}
