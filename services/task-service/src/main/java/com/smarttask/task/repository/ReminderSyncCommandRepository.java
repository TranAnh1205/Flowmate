package com.smarttask.task.repository;

import com.smarttask.task.domain.ReminderSyncCommand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReminderSyncCommandRepository extends JpaRepository<ReminderSyncCommand, UUID> {
    Optional<ReminderSyncCommand> findByTaskId(UUID taskId);
    List<ReminderSyncCommand> findTop50ByNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(Instant now);
}
