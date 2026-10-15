package com.smarttask.task.repository;
import com.smarttask.task.domain.TaskTag; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface TaskTagRepository extends JpaRepository<TaskTag,UUID>{List<TaskTag> findAllByTaskId(UUID taskId);List<TaskTag> findAllByTagId(UUID tagId);void deleteAllByTaskId(UUID taskId);}
