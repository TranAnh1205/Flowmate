package com.smarttask.notification.domain;
import jakarta.persistence.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="reminders",uniqueConstraints=@UniqueConstraint(columnNames={"task_id","scheduled_at","type"}))
public class Reminder{
 @Id private UUID id; @Column(nullable=false)private UUID userId;@Column(nullable=false)private UUID taskId;@Column(nullable=false)private Instant scheduledAt;
 @Column(nullable=false,length=30)private String type;@Column(nullable=false,length=20)private String status;private Instant processedAt;
 @Column(nullable=false,updatable=false)private Instant createdAt;@Column(nullable=false)private Instant updatedAt;
 @PrePersist void pre(){if(id==null)id=UUID.randomUUID();Instant n=Instant.now();createdAt=n;updatedAt=n;if(status==null)status="PENDING";}@PreUpdate void upd(){updatedAt=Instant.now();}
 public UUID getId(){return id;}public UUID getUserId(){return userId;}public void setUserId(UUID v){userId=v;}public UUID getTaskId(){return taskId;}public void setTaskId(UUID v){taskId=v;}public Instant getScheduledAt(){return scheduledAt;}public void setScheduledAt(Instant v){scheduledAt=v;}public String getType(){return type;}public void setType(String v){type=v;}public String getStatus(){return status;}public void setStatus(String v){status=v;}public Instant getProcessedAt(){return processedAt;}public void setProcessedAt(Instant v){processedAt=v;}
}

