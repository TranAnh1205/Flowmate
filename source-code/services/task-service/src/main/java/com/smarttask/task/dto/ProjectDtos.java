package com.smarttask.task.dto;

import com.smarttask.task.domain.TaskPriority;
import com.smarttask.task.domain.TaskStatus;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ProjectDtos {
    private ProjectDtos() {}

    public record CreateProjectRequest(@NotBlank @Size(max = 160) String name,
                                       @Size(max = 5000) String description) {}
    public record UpdateProjectRequest(@NotBlank @Size(max = 160) String name,
                                       @Size(max = 5000) String description,
                                       @NotNull @Pattern(regexp = "ACTIVE|ARCHIVED") String status) {}
    public record ProjectResponse(UUID id, String name, String description, String status,
                                  UUID ownerId, String myRole, long memberCount,
                                  long taskCount, long completedTaskCount, Instant createdAt) {}
    public record MemberResponse(UUID userId, String email, String role, Instant joinedAt) {}
    public record InviteMemberRequest(@NotBlank @Email @Size(max = 255) String email) {}
    public record InvitationResponse(UUID id, UUID projectId, String projectName,
                                     String invitedEmail, Instant expiresAt) {}
    public record AcceptInvitationRequest(@NotNull UUID invitationId) {}
    public record CreateProjectTaskRequest(@NotBlank @Size(max = 255) String title,
                                           @Size(max = 10000) String description,
                                           TaskStatus status, TaskPriority priority,
                                           UUID assigneeId, Instant startAt, Instant dueAt) {
        public CreateProjectTaskRequest(String title, String description, TaskStatus status,
                                        TaskPriority priority, UUID assigneeId, Instant dueAt) {
            this(title, description, status, priority, assigneeId, null, dueAt);
        }
    }
    public record UpdateProjectTaskRequest(@NotBlank @Size(max = 255) String title,
                                           @Size(max = 10000) String description,
                                           @NotNull TaskStatus status, @NotNull TaskPriority priority,
                                           UUID assigneeId, Instant startAt, Instant dueAt,
                                           @NotNull @PositiveOrZero Long version) {
        public UpdateProjectTaskRequest(String title, String description, TaskStatus status,
                                        TaskPriority priority, UUID assigneeId, Instant dueAt,
                                        Long version) {
            this(title, description, status, priority, assigneeId, null, dueAt, version);
        }
    }
    public record ChangeProjectTaskStatusRequest(@NotNull TaskStatus status,
                                                 @NotNull @PositiveOrZero Long version) {}
    public record ProjectTaskResponse(UUID id, UUID projectId, String title, String description,
                                      TaskStatus status, TaskPriority priority, UUID createdBy,
                                      UUID assigneeId, Instant startAt, Instant dueAt, Instant completedAt,
                                      long version, Instant createdAt, Instant updatedAt) {}
    public record ProjectTaskPage(List<ProjectTaskResponse> content, int page, int size,
                                  long totalElements, int totalPages) {}
}
