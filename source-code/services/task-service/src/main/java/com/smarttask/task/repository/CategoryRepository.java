package com.smarttask.task.repository;
import com.smarttask.task.domain.Category; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface CategoryRepository extends JpaRepository<Category,UUID>{List<Category> findAllByUserIdOrderByNameAsc(UUID userId); Optional<Category> findByIdAndUserId(UUID id,UUID userId); boolean existsByUserIdAndNameIgnoreCase(UUID userId,String name);}

