package com.smarttask.ai.dto;
import jakarta.validation.constraints.*;import java.time.*;import java.util.*;
public final class AiDtos{private AiDtos(){}
 public record ParseTaskRequest(@NotBlank @Size(max=2000)String prompt){}
 public record SubtaskSuggestion(String title,Integer estimatedMinutes,int position){}
 public record ParsedTaskResponse(String title,String description,String priority,OffsetDateTime startAt,OffsetDateTime dueAt,Integer estimatedMinutes,String categoryName,Integer reminderMinutesBefore,List<SubtaskSuggestion>subtasks,List<String>warnings){}
 public record BreakdownRequest(@NotBlank @Size(max=255)String title,@Size(max=5000)String description,@Min(1)@Max(10)Integer maximumSubtasks){}
 public record BreakdownResponse(List<SubtaskSuggestion>subtasks){}
 public record PriorityRequest(@NotBlank String title,String description,OffsetDateTime dueAt,Integer estimatedMinutes){}
 public record PriorityResponse(String priority,String reason){}
 public record DailyPlanRequest(@NotNull LocalDate date){}
 public record ScheduleItem(UUID taskId,String title,LocalTime startAt,LocalTime endAt,String reason){}
 public record DailyPlanResponse(LocalDate date,List<ScheduleItem>schedule,List<UUID>unscheduledTasks,List<String>warnings){}
 public record WeeklySummaryResponse(String summary,long totalTasks,long completedTasks,long overdueTasks,double completionRate,List<String>strengths,List<String>suggestions){}
}

