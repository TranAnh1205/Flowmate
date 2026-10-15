package com.smarttask.task.controller;

import com.smarttask.task.domain.TaskStatus;
import com.smarttask.task.repository.TaskRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/statistics/tasks")
public class AdminTaskStatisticsController {
    private final TaskRepository tasks;

    public AdminTaskStatisticsController(TaskRepository tasks) {
        this.tasks = tasks;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER_VIEW')")
    public TaskSystemStatistics statistics() {
        long total = tasks.countByDeletedAtIsNull();
        long todo = tasks.countByDeletedAtIsNullAndStatus(TaskStatus.TODO);
        long inProgress = tasks.countByDeletedAtIsNullAndStatus(TaskStatus.IN_PROGRESS);
        long completed = tasks.countByDeletedAtIsNullAndStatus(TaskStatus.COMPLETED);
        long cancelled = tasks.countByDeletedAtIsNullAndStatus(TaskStatus.CANCELLED);
        long overdue = tasks.countByDeletedAtIsNullAndDueAtBeforeAndStatusNotIn(
                Instant.now(), List.of(TaskStatus.COMPLETED, TaskStatus.CANCELLED));
        Instant today = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant week = LocalDate.now(ZoneOffset.UTC).with(DayOfWeek.MONDAY)
                .atStartOfDay().toInstant(ZoneOffset.UTC);
        double completionRate = total == 0 ? 0 : Math.round(completed * 10_000.0 / total) / 100.0;
        return new TaskSystemStatistics(total, todo, inProgress, completed, cancelled, overdue,
                tasks.countByDeletedAtIsNullAndCreatedAtGreaterThanEqual(today),
                tasks.countByDeletedAtIsNullAndCreatedAtGreaterThanEqual(week),
                completionRate);
    }

    public record TaskSystemStatistics(
            long totalTasks,
            long todo,
            long inProgress,
            long completed,
            long cancelled,
            long overdue,
            long createdToday,
            long createdThisWeek,
            double completionRate
    ) {}
}
