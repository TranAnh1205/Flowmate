package com.smarttask.task.service;

import com.smarttask.task.domain.ReminderSyncCommand;
import com.smarttask.task.repository.ReminderSyncCommandRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class ReminderSyncQueueService {
    private final ReminderSyncCommandRepository commands;

    public ReminderSyncQueueService(ReminderSyncCommandRepository commands) {
        this.commands = commands;
    }

    public void enqueueUpsert(UUID userId, UUID taskId, Instant scheduledAt) {
        enqueue(userId, taskId, "UPSERT", scheduledAt);
    }

    public void enqueueDelete(UUID userId, UUID taskId) {
        enqueue(userId, taskId, "DELETE", null);
    }

    private void enqueue(UUID userId, UUID taskId, String action, Instant scheduledAt) {
        ReminderSyncCommand command = commands.findByTaskId(taskId).orElseGet(ReminderSyncCommand::new);
        command.setUserId(userId);
        command.setTaskId(taskId);
        command.setAction(action);
        command.setScheduledAt(scheduledAt);
        command.setAttempts(0);
        command.setNextAttemptAt(Instant.now());
        command.setLastError(null);
        commands.save(command);
    }
}
