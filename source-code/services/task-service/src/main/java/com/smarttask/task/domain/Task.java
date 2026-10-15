package com.smarttask.task.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tasks")
public class Task {
    @Id private UUID id;
    @Column(nullable = false) private UUID userId;
    private UUID categoryId;
    private UUID goalId;
    @Column(nullable = false, length = 255) private String title;
    @Column(columnDefinition = "TEXT") private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private TaskStatus status;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private TaskPriority priority;
    private Instant startAt;
    private Instant dueAt;
    private Integer estimatedMinutes;
    private Integer actualMinutes;
    private Integer reminderMinutesBefore;
    @Column(nullable = false) private int position;
    @Column(length = 255) private String recurrenceRule;
    private UUID parentRecurrenceId;
    private Instant completedAt;
    private Instant deletedAt;
    @Version private long version;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;

    @PrePersist void prePersist() { if (id == null) id = UUID.randomUUID(); Instant now=Instant.now(); createdAt=now; updatedAt=now; }
    @PreUpdate void preUpdate() { updatedAt=Instant.now(); }
    public UUID getId(){return id;} public UUID getUserId(){return userId;} public void setUserId(UUID v){userId=v;}
    public UUID getCategoryId(){return categoryId;} public void setCategoryId(UUID v){categoryId=v;}
    public UUID getGoalId(){return goalId;} public void setGoalId(UUID v){goalId=v;}
    public String getTitle(){return title;} public void setTitle(String v){title=v;}
    public String getDescription(){return description;} public void setDescription(String v){description=v;}
    public TaskStatus getStatus(){return status;} public void setStatus(TaskStatus v){status=v;}
    public TaskPriority getPriority(){return priority;} public void setPriority(TaskPriority v){priority=v;}
    public Instant getStartAt(){return startAt;} public void setStartAt(Instant v){startAt=v;}
    public Instant getDueAt(){return dueAt;} public void setDueAt(Instant v){dueAt=v;}
    public Integer getEstimatedMinutes(){return estimatedMinutes;} public void setEstimatedMinutes(Integer v){estimatedMinutes=v;}
    public Integer getActualMinutes(){return actualMinutes;} public void setActualMinutes(Integer v){actualMinutes=v;}
    public Integer getReminderMinutesBefore(){return reminderMinutesBefore;} public void setReminderMinutesBefore(Integer v){reminderMinutesBefore=v;}
    public int getPosition(){return position;} public void setPosition(int v){position=v;}
    public String getRecurrenceRule(){return recurrenceRule;} public void setRecurrenceRule(String v){recurrenceRule=v;}
    public UUID getParentRecurrenceId(){return parentRecurrenceId;} public void setParentRecurrenceId(UUID v){parentRecurrenceId=v;}
    public Instant getCompletedAt(){return completedAt;} public void setCompletedAt(Instant v){completedAt=v;}
    public Instant getDeletedAt(){return deletedAt;} public void setDeletedAt(Instant v){deletedAt=v;}
    public long getVersion(){return version;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
