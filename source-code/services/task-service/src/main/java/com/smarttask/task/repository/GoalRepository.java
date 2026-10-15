package com.smarttask.task.repository;
import com.smarttask.task.domain.Goal; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface GoalRepository extends JpaRepository<Goal,UUID>{List<Goal> findAllByUserIdOrderByTargetDateAsc(UUID userId); Optional<Goal> findByIdAndUserId(UUID id,UUID userId);}

