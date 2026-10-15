package com.smarttask.task.domain;
import jakarta.persistence.*; import java.time.*; import java.util.UUID;
@Entity @Table(name="goals")
public class Goal {
 @Id private UUID id; @Column(nullable=false) private UUID userId; @Column(nullable=false,length=160) private String title;
 @Column(columnDefinition="TEXT") private String description; @Column(nullable=false,length=20) private String status; private LocalDate targetDate;
 @Column(nullable=false,updatable=false) private Instant createdAt; @Column(nullable=false) private Instant updatedAt;
 @PrePersist void pre(){if(id==null)id=UUID.randomUUID();Instant n=Instant.now();createdAt=n;updatedAt=n;} @PreUpdate void upd(){updatedAt=Instant.now();}
 public UUID getId(){return id;} public UUID getUserId(){return userId;} public void setUserId(UUID v){userId=v;}
 public String getTitle(){return title;} public void setTitle(String v){title=v;} public String getDescription(){return description;} public void setDescription(String v){description=v;}
 public String getStatus(){return status;} public void setStatus(String v){status=v;} public LocalDate getTargetDate(){return targetDate;} public void setTargetDate(LocalDate v){targetDate=v;}
}

