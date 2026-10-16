package com.smarttask.task.dto;

import com.smarttask.task.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public final class TaskDtos {
 private TaskDtos(){}
 public record SubtaskInput(@NotBlank @Size(max=255) String title){}
 public record CreateTaskRequest(@NotBlank @Size(max=255) String title, @Size(max=10000) String description,
   TaskStatus status, TaskPriority priority, UUID categoryId, UUID goalId, Instant startAt, Instant dueAt,
   @Positive Integer estimatedMinutes, @PositiveOrZero Integer actualMinutes, @Size(max=255) String recurrenceRule,
   Set<UUID> tagIds, List<@Valid SubtaskInput> subtasks, @PositiveOrZero Integer reminderMinutesBefore){}
 public record UpdateTaskRequest(@NotBlank @Size(max=255) String title, @Size(max=10000) String description,
   @NotNull TaskStatus status, @NotNull TaskPriority priority, UUID categoryId, UUID goalId, Instant startAt, Instant dueAt,
   @Positive Integer estimatedMinutes, @PositiveOrZero Integer actualMinutes, @Size(max=255) String recurrenceRule, Set<UUID> tagIds,
   @PositiveOrZero Integer reminderMinutesBefore){}
 public record ChangeStatusRequest(@NotNull TaskStatus status){}
 public record ReorderItem(@NotNull UUID taskId,@NotNull TaskStatus status,@PositiveOrZero int position){}
 public record ReorderRequest(@NotEmpty List<@Valid ReorderItem> items){}
 public record SubtaskResponse(UUID id,String title,boolean completed,int position,Instant completedAt){}
 public record TagResponse(UUID id,String name,String color){}
 public record TaskResponse(UUID id,String title,String description,TaskStatus status,TaskPriority priority,
   UUID categoryId,UUID goalId,Instant startAt,Instant dueAt,Integer estimatedMinutes,Integer actualMinutes,
   Integer reminderMinutesBefore,int position,String recurrenceRule,Instant completedAt,long version,List<SubtaskResponse> subtasks,List<TagResponse> tags,
   Instant createdAt,Instant updatedAt){}
 public record PageResponse<T>(List<T> content,int page,int size,long totalElements,int totalPages){}
 public record SubtaskRequest(@NotBlank @Size(max=255) String title,@PositiveOrZero Integer position){}
 public record CategoryRequest(@NotBlank @Size(max=80) String name,@NotBlank @Pattern(regexp="^#[0-9A-Fa-f]{6}$") String color,@Size(max=50) String icon){}
 public record CategoryResponse(UUID id,String name,String color,String icon){}
 public record TagRequest(@NotBlank @Size(max=60) String name,@NotBlank @Pattern(regexp="^#[0-9A-Fa-f]{6}$") String color){}
 public record GoalRequest(@NotBlank @Size(max=160) String title,@Size(max=5000) String description,@Pattern(regexp="ACTIVE|COMPLETED|ARCHIVED") String status,LocalDate targetDate){}
 public record GoalResponse(UUID id,String title,String description,String status,LocalDate targetDate){}
 public record DashboardResponse(long totalTasks,long todo,long inProgress,long completed,long cancelled,long overdue,long dueToday,double weeklyCompletionRate){}
}
