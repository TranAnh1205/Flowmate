package com.smarttask.task.repository;
import com.smarttask.task.domain.*; import org.springframework.data.domain.*; import org.springframework.data.jpa.repository.*; import java.time.Instant; import java.util.*;
public interface TaskRepository extends JpaRepository<Task,UUID>, JpaSpecificationExecutor<Task> {
 Optional<Task> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);
 List<Task> findAllByUserIdAndDeletedAtIsNullAndDueAtBetweenOrderByDueAtAsc(UUID userId, Instant from, Instant to);
 List<Task> findAllByUserIdAndDeletedAtIsNullAndDueAtBeforeAndStatusNotInOrderByDueAtAsc(UUID userId, Instant now, Collection<TaskStatus> statuses);
 long countByUserIdAndDeletedAtIsNull(UUID userId);
 long countByUserIdAndDeletedAtIsNullAndStatus(UUID userId, TaskStatus status);
 long countByUserIdAndDeletedAtIsNullAndStatusAndDueAtBetween(UUID userId, TaskStatus status, Instant from, Instant to);
 long countByUserIdAndDeletedAtIsNullAndDueAtBeforeAndStatusNotIn(UUID userId, Instant now, Collection<TaskStatus> statuses);
 long countByDeletedAtIsNull();
 long countByDeletedAtIsNullAndStatus(TaskStatus status);
 long countByDeletedAtIsNullAndDueAtBeforeAndStatusNotIn(Instant now, Collection<TaskStatus> statuses);
 long countByDeletedAtIsNullAndCreatedAtGreaterThanEqual(Instant since);
}
