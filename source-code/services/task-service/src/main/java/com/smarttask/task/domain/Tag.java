package com.smarttask.task.domain;
import jakarta.persistence.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="tags")
public class Tag {
 @Id private UUID id; @Column(nullable=false) private UUID userId; @Column(nullable=false,length=60) private String name;
 @Column(nullable=false,length=20) private String color; @Column(nullable=false,updatable=false) private Instant createdAt; @Column(nullable=false) private Instant updatedAt;
 @PrePersist void pre(){if(id==null)id=UUID.randomUUID();Instant n=Instant.now();createdAt=n;updatedAt=n;} @PreUpdate void upd(){updatedAt=Instant.now();}
 public UUID getId(){return id;} public UUID getUserId(){return userId;} public void setUserId(UUID v){userId=v;}
 public String getName(){return name;} public void setName(String v){name=v;} public String getColor(){return color;} public void setColor(String v){color=v;}
}

