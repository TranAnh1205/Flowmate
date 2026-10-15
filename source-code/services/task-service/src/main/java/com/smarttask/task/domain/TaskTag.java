package com.smarttask.task.domain;
import jakarta.persistence.*; import java.util.UUID;
@Entity @Table(name="task_tags", uniqueConstraints=@UniqueConstraint(columnNames={"task_id","tag_id"}))
public class TaskTag {
 @Id private UUID id; @Column(nullable=false) private UUID taskId; @Column(nullable=false) private UUID tagId;
 @PrePersist void pre(){if(id==null)id=UUID.randomUUID();}
 public UUID getId(){return id;} public UUID getTaskId(){return taskId;} public void setTaskId(UUID v){taskId=v;} public UUID getTagId(){return tagId;} public void setTagId(UUID v){tagId=v;}
}

