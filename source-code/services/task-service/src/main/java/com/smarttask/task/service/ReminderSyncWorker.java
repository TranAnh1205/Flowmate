package com.smarttask.task.service;

import com.smarttask.task.domain.ReminderSyncCommand;
import com.smarttask.task.repository.ReminderSyncCommandRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class ReminderSyncWorker {
    private static final Logger log = LoggerFactory.getLogger(ReminderSyncWorker.class);
    private static final int MAX_ERROR_LENGTH = 1000;
    private final ReminderSyncCommandRepository commands;
    private final ReminderGateway reminderGateway;

    public ReminderSyncWorker(ReminderSyncCommandRepository commands,
                              ReminderGateway reminderGateway) {
        this.commands = commands;
        this.reminderGateway = reminderGateway;
    }

    @Scheduled(fixedDelayString = "${app.reminder-sync.poll-delay-ms:2000}")
    @Transactional
    public void processPending() {
        for (ReminderSyncCommand command : commands
                .findTop50ByNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(Instant.now())) {
            deliver(command.getId());
        }
    }

    public void deliver(java.util.UUID commandId) {
        ReminderSyncCommand command = commands.findById(commandId).orElse(null);
        if (command == null) return;
        try {
            if ("DELETE".equals(command.getAction())) {
                reminderGateway.delete(command.getUserId(), command.getTaskId());
            } else {
                reminderGateway.upsert(command.getUserId(), command.getTaskId(), command.getScheduledAt());
            }
            commands.delete(command);
        } catch (RuntimeException error) {
            int attempts = command.getAttempts() + 1;
            long delaySeconds = Math.min(300, 1L << Math.min(attempts, 8));
            command.setAttempts(attempts);
            command.setNextAttemptAt(Instant.now().plus(delaySeconds, ChronoUnit.SECONDS));
            String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            command.setLastError(message.substring(0, Math.min(message.length(), MAX_ERROR_LENGTH)));
            log.warn("Reminder synchronization failed for task {} (attempt {}): {}",
                    command.getTaskId(), attempts, error.getClass().getSimpleName());
        }
    }
}
