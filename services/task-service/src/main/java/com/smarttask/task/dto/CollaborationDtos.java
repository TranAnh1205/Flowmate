package com.smarttask.task.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class CollaborationDtos {
    private CollaborationDtos() {}

    public record TextRequest(@NotBlank @Size(max = 5000) String body) {}
    public record ChecklistRequest(@NotBlank @Size(max = 500) String title) {}
    public record ChecklistUpdateRequest(@NotBlank @Size(max = 500) String title, boolean done) {}
    public record LabelRequest(@NotBlank @Size(max = 80) String name,
                               @NotBlank @Pattern(regexp = "slate|blue|green|yellow|rose|purple") String color) {}

    public record Comment(UUID id, UUID taskId, UUID authorId, String body,
                          Instant createdAt, Instant updatedAt) {}
    public record ChecklistItem(UUID id, UUID taskId, String title, boolean done,
                                long position, Instant createdAt) {}
    public record Attachment(UUID id, UUID taskId, String originalName,
                             String contentType, long byteSize, UUID uploadedBy,
                             Instant createdAt) {}
    public record Message(UUID id, UUID boardId, UUID authorId, String body,
                          Instant createdAt) {}
    public record Event(long id, UUID boardId, UUID taskId, UUID actorId,
                        String kind, Instant createdAt) {}
    public record Label(UUID id, UUID boardId, String name, String color) {}
}
