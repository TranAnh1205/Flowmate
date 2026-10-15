package com.smarttask.task.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="subtasks")
public class Subtask {
    @Id private UUID id;
    @Column(nullable=false) private UUID taskId;
    @Column(nullable=false, length=255) private String title;
    @Column(nullable=false) private boolean completed;
    @Column(nullable=false) private int position;
    private Instant completedAt;
    @Column(nullable=false, updatable=false) private Instant createdAt;
    @Column(nullable=false) private Instant updatedAt;
    @PrePersist void prePersist(){if(id==null)id=UUID.randomUUID(); Instant now=Instant.now();createdAt=now;updatedAt=now;}
    @PreUpdate void preUpdate(){updatedAt=Instant.now();}
    public UUID getId(){return id;} public UUID getTaskId(){return taskId;} public void setTaskId(UUID v){taskId=v;}
    public String getTitle(){return title;} public void setTitle(String v){title=v;} public boolean isCompleted(){return completed;}
    public void setCompleted(boolean v){completed=v;} public int getPosition(){return position;} public void setPosition(int v){position=v;}
    public Instant getCompletedAt(){return completedAt;} public void setCompletedAt(Instant v){completedAt=v;}
}

