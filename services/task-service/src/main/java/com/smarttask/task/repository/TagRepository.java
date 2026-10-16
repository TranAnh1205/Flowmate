package com.smarttask.task.repository;
import com.smarttask.task.domain.Tag; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface TagRepository extends JpaRepository<Tag,UUID>{List<Tag> findAllByUserIdOrderByNameAsc(UUID userId); Optional<Tag> findByIdAndUserId(UUID id,UUID userId); boolean existsByUserIdAndNameIgnoreCase(UUID userId,String name);}
