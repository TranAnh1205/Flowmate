package com.smarttask.task.repository;
import com.smarttask.task.domain.Subtask; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface SubtaskRepository extends JpaRepository<Subtask,UUID>{List<Subtask> findAllByTaskIdOrderByPositionAsc(UUID taskId); void deleteAllByTaskId(UUID taskId);}

