package com.smarttask.task.dto;

import com.smarttask.task.domain.TaskPriority;
import com.smarttask.task.domain.TaskStatus;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class BoardDtos {
    private BoardDtos() {}

    public record BoardSummary(UUID id, UUID projectId, String title, String description,
                               String visibility, String background, String myRole,
                               boolean isDefault, Instant createdAt) {}
    public record BoardDetails(BoardSummary board, List<BoardList> lists) {}
    public record RecentBoard(BoardSummary board, String projectName, Instant viewedAt) {}
    public record BoardList(UUID id, String title, long position, String workflowStatus,
                            List<BoardCard> cards) {}
    public record BoardCard(UUID id, String title, String description, TaskStatus status,
                            TaskPriority priority, UUID assigneeId, Instant startAt, Instant dueAt,
                            long version, long position) {}
    public record CreateBoardRequest(@NotBlank @Size(max = 160) String title,
                                     @Size(max = 5000) String description,
                                     @Pattern(regexp = "WORKSPACE|PRIVATE") String visibility,
                                     @Size(max = 40) String background,
                                     @Pattern(regexp = "basic|sprint|content") String template) {}
    public record UpdateBoardRequest(@NotBlank @Size(max = 160) String title,
                                     @Size(max = 5000) String description,
                                     @NotNull @Pattern(regexp = "WORKSPACE|PRIVATE") String visibility,
                                     @NotBlank @Size(max = 40) String background) {}
    public record CopyBoardRequest(@NotBlank @Size(max = 160) String title) {}
    public record ListRequest(@NotBlank @Size(max = 160) String title) {}
    public record ReorderListsRequest(@NotEmpty List<@NotNull UUID> listIds) {}
    public record MoveCardRequest(@NotNull UUID listId, @Min(0) int index,
                                  @NotNull @PositiveOrZero Long version) {}
    public record CreateCardRequest(@NotBlank @Size(max = 255) String title,
                                    @Size(max = 10000) String description,
                                    TaskPriority priority, UUID assigneeId, Instant startAt,
                                    Instant dueAt) {}
    public record CopyCardRequest(@NotNull UUID targetBoardId, @NotNull UUID listId) {}
    public record TransferCardRequest(@NotNull UUID targetBoardId, @NotNull UUID listId,
                                      @NotNull @PositiveOrZero Long version) {}
}
