package com.smarttask.task.service;

import com.smarttask.task.domain.*;
import com.smarttask.task.dto.TaskDtos.CreateTaskRequest;
import com.smarttask.task.dto.TaskDtos.SubtaskInput;
import com.smarttask.task.dto.TaskDtos.UpdateTaskRequest;
import com.smarttask.task.exception.TaskApiException;
import com.smarttask.task.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {
    @Mock TaskRepository tasks;
    @Mock SubtaskRepository subtasks;
    @Mock CategoryRepository categories;
    @Mock TagRepository tags;
    @Mock TaskTagRepository taskTags;
    @Mock GoalRepository goals;
    @Mock ReminderSyncQueueService reminderSyncQueue;
    TaskService service;

    @BeforeEach
    void setUp() {
        service = new TaskService(tasks, subtasks, categories, tags, taskTags, goals, reminderSyncQueue);
        lenient().when(subtasks.findAllByTaskIdOrderByPositionAsc(any())).thenReturn(List.of());
        lenient().when(taskTags.findAllByTaskId(any())).thenReturn(List.of());
    }

    @Test
    void createUsesDefaultsAndPersistsChecklist() {
        when(tasks.save(any(Task.class))).thenAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            ReflectionTestUtils.invokeMethod(task, "prePersist");
            return task;
        });
        CreateTaskRequest request = new CreateTaskRequest("  Viết báo cáo  ", "Mô tả", null, null,
                null, null, null, null, 60, null, null, Set.of(),
                List.of(new SubtaskInput("Bước 1"), new SubtaskInput("Bước 2")), null);

        var response = service.create(UUID.randomUUID(), request, "Bearer token");

        assertEquals("Viết báo cáo", response.title());
        assertEquals(TaskStatus.TODO, response.status());
        assertEquals(TaskPriority.MEDIUM, response.priority());
        verify(subtasks, times(2)).save(any(Subtask.class));
    }

    @Test
    void createRejectsDeadlineBeforeStart() {
        Instant start = Instant.now();
        CreateTaskRequest request = new CreateTaskRequest("Task", null, null, null, null, null,
                start, start.minusSeconds(1), null, null, null, Set.of(), List.of(), null);

        TaskApiException error = assertThrows(TaskApiException.class,
                () -> service.create(UUID.randomUUID(), request, "Bearer token"));

        assertEquals("INVALID_TASK_DATE", error.code());
        verify(tasks, never()).save(any());
    }

    @Test
    void createStoresReminderSettingAndQueuesSynchronization() {
        UUID userId = UUID.randomUUID();
        Instant dueAt = Instant.now().plusSeconds(3600);
        when(tasks.save(any(Task.class))).thenAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            ReflectionTestUtils.invokeMethod(task, "prePersist");
            return task;
        });
        CreateTaskRequest request = new CreateTaskRequest("Nhắc việc", null, null, null,
                null, null, null, dueAt, 60, null, null, Set.of(), List.of(), 30);

        var response = service.create(userId, request, "Bearer token");

        assertEquals(30, response.reminderMinutesBefore());
        verify(reminderSyncQueue).enqueueUpsert(userId, response.id(), dueAt.minusSeconds(1800));
    }

    @Test
    void completingTaskSetsCompletionTimestamp() {
        UUID userId = UUID.randomUUID();
        Task task = task(userId, TaskStatus.IN_PROGRESS);
        when(tasks.findByIdAndUserIdAndDeletedAtIsNull(task.getId(), userId)).thenReturn(Optional.of(task));

        var response = service.status(userId, task.getId(), TaskStatus.COMPLETED, "Bearer token");

        assertEquals(TaskStatus.COMPLETED, response.status());
        assertNotNull(response.completedAt());
        verify(reminderSyncQueue).enqueueDelete(userId, task.getId());
    }

    @Test
    void completingTaskCompletesItsChecklist() {
        UUID userId = UUID.randomUUID();
        Task task = task(userId, TaskStatus.IN_PROGRESS);
        Subtask first = new Subtask();
        first.setTaskId(task.getId());
        first.setTitle("Bước 1");
        Subtask second = new Subtask();
        second.setTaskId(task.getId());
        second.setTitle("Bước 2");
        when(tasks.findByIdAndUserIdAndDeletedAtIsNull(task.getId(), userId)).thenReturn(Optional.of(task));
        when(subtasks.findAllByTaskIdOrderByPositionAsc(task.getId()))
                .thenReturn(List.of(first, second));

        var response = service.status(userId, task.getId(), TaskStatus.COMPLETED, "Bearer token");

        assertEquals(TaskStatus.COMPLETED, response.status());
        assertTrue(first.isCompleted());
        assertTrue(second.isCompleted());
        assertNotNull(first.getCompletedAt());
        verify(subtasks).saveAll(List.of(first, second));
    }

    @Test
    void updatingTaskFlushesRemovedTagsBeforeReinsertingMappings() {
        UUID userId = UUID.randomUUID();
        UUID tagId = UUID.randomUUID();
        Task task = task(userId, TaskStatus.TODO);
        Tag tag = new Tag();
        tag.setUserId(userId);
        tag.setName("Quan trọng");
        tag.setColor("#EF4444");
        when(tasks.findByIdAndUserIdAndDeletedAtIsNull(task.getId(), userId)).thenReturn(Optional.of(task));
        when(tags.findByIdAndUserId(tagId, userId)).thenReturn(Optional.of(tag));

        UpdateTaskRequest request = new UpdateTaskRequest("Task cập nhật", null, TaskStatus.TODO,
                TaskPriority.HIGH, null, null, null, null, 90, null, null, Set.of(tagId), null);

        service.update(userId, task.getId(), request, "Bearer token");

        InOrder order = inOrder(taskTags);
        order.verify(taskTags).deleteAllByTaskId(task.getId());
        order.verify(taskTags).flush();
        order.verify(taskTags).saveAll(anyList());
        order.verify(taskTags).flush();
    }

    @Test
    void dashboardCalculatesWeeklyCompletionFromTasksDueThisWeek() {
        UUID userId = UUID.randomUUID();
        List<Task> dueThisWeek = List.of(task(userId, TaskStatus.COMPLETED), task(userId, TaskStatus.COMPLETED),
                task(userId, TaskStatus.COMPLETED), task(userId, TaskStatus.TODO));
        when(tasks.findAllByUserIdAndDeletedAtIsNullAndDueAtBetweenOrderByDueAtAsc(eq(userId), any(), any()))
                .thenReturn(dueThisWeek);
        when(tasks.countByUserIdAndDeletedAtIsNullAndStatusAndDueAtBetween(eq(userId), eq(TaskStatus.COMPLETED), any(), any()))
                .thenReturn(3L);

        var dashboard = service.dashboard(userId, ZoneId.of("Asia/Bangkok"));

        assertEquals(75.0, dashboard.weeklyCompletionRate());
    }

    private Task task(UUID userId, TaskStatus status) {
        Task task = new Task();
        ReflectionTestUtils.invokeMethod(task, "prePersist");
        task.setUserId(userId);
        task.setTitle("Task");
        task.setStatus(status);
        task.setPriority(TaskPriority.MEDIUM);
        task.setDueAt(Instant.now().plusSeconds(3600));
        return task;
    }
}
