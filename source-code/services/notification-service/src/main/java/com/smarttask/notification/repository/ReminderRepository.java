package com.smarttask.notification.repository;
import com.smarttask.notification.domain.Reminder;import org.springframework.data.jpa.repository.JpaRepository;import java.time.Instant;import java.util.*;
public interface ReminderRepository extends JpaRepository<Reminder,UUID>{List<Reminder> findTop100ByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(String status,Instant now);List<Reminder> findAllByTaskIdAndUserId(UUID taskId,UUID userId);long countByStatus(String status);long countByStatusAndScheduledAtBefore(String status,Instant now);}
