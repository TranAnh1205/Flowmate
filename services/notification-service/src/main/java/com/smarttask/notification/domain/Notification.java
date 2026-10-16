package com.smarttask.notification.domain;
import jakarta.persistence.*;import java.time.Instant;import java.util.UUID;
@Entity @Table(name="notifications")
public class Notification{
 @Id private UUID id;@Column(nullable=false)private UUID userId;private UUID taskId;@Column(nullable=false,length=180)private String title;@Column(nullable=false,length=1000)private String message;@Column(nullable=false,length=30)private String type;@Column(nullable=false)private boolean read;@Column(nullable=false,updatable=false)private Instant createdAt;private Instant readAt;
 @PrePersist void pre(){if(id==null)id=UUID.randomUUID();createdAt=Instant.now();}
 public UUID getId(){return id;}public UUID getUserId(){return userId;}public void setUserId(UUID v){userId=v;}public UUID getTaskId(){return taskId;}public void setTaskId(UUID v){taskId=v;}public String getTitle(){return title;}public void setTitle(String v){title=v;}public String getMessage(){return message;}public void setMessage(String v){message=v;}public String getType(){return type;}public void setType(String v){type=v;}public boolean isRead(){return read;}public void setRead(boolean v){read=v;}public Instant getCreatedAt(){return createdAt;}public Instant getReadAt(){return readAt;}public void setReadAt(Instant v){readAt=v;}
}

