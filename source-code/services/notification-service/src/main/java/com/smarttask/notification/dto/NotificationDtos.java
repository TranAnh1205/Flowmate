package com.smarttask.notification.dto;
import jakarta.validation.constraints.*;import java.time.Instant;import java.util.*;
public final class NotificationDtos{private NotificationDtos(){}
 public record ReminderRequest(@NotNull UUID taskId,@NotNull Instant scheduledAt,@NotBlank @Pattern(regexp="DEADLINE|OVERDUE|DAILY_PLAN")String type){}
 public record ReminderSyncRequest(@NotNull Instant scheduledAt,@NotBlank @Pattern(regexp="DEADLINE|OVERDUE|DAILY_PLAN")String type){}
 public record InternalReminderSyncRequest(@NotNull UUID userId,@NotNull Instant scheduledAt,@NotBlank @Pattern(regexp="DEADLINE|OVERDUE|DAILY_PLAN")String type){}
 public record ReminderResponse(UUID id,UUID taskId,Instant scheduledAt,String type,String status){}
 public record NotificationResponse(UUID id,UUID taskId,String title,String message,String type,boolean read,Instant createdAt,Instant readAt){}
 public record PageResponse<T>(List<T>content,int page,int size,long totalElements,int totalPages){}
 public record UnreadCountResponse(long unreadCount){}
}
