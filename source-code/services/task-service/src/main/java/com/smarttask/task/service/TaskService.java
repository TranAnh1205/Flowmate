package com.smarttask.task.service;

import com.smarttask.task.domain.Category;
import com.smarttask.task.domain.Goal;
import com.smarttask.task.domain.Subtask;
import com.smarttask.task.domain.Tag;
import com.smarttask.task.domain.Task;
import com.smarttask.task.domain.TaskPriority;
import com.smarttask.task.domain.TaskStatus;
import com.smarttask.task.domain.TaskTag;
import com.smarttask.task.dto.TaskDtos.ChangeStatusRequest;
import com.smarttask.task.dto.TaskDtos.CreateTaskRequest;
import com.smarttask.task.dto.TaskDtos.DashboardResponse;
import com.smarttask.task.dto.TaskDtos.PageResponse;
import com.smarttask.task.dto.TaskDtos.ReorderItem;
import com.smarttask.task.dto.TaskDtos.ReorderRequest;
import com.smarttask.task.dto.TaskDtos.SubtaskInput;
import com.smarttask.task.dto.TaskDtos.SubtaskResponse;
import com.smarttask.task.dto.TaskDtos.TagResponse;
import com.smarttask.task.dto.TaskDtos.TaskResponse;
import com.smarttask.task.dto.TaskDtos.UpdateTaskRequest;
import com.smarttask.task.exception.TaskApiException;
import com.smarttask.task.repository.CategoryRepository;
import com.smarttask.task.repository.GoalRepository;
import com.smarttask.task.repository.SubtaskRepository;
import com.smarttask.task.repository.TagRepository;
import com.smarttask.task.repository.TaskRepository;
import com.smarttask.task.repository.TaskTagRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class TaskService {
    private final TaskRepository tasks;
    private final SubtaskRepository subtasks;
    private final CategoryRepository categories;
    private final TagRepository tags;
    private final TaskTagRepository taskTags;
    private final GoalRepository goals;
    private final ReminderSyncQueueService reminderSyncQueue;

    public TaskService(TaskRepository tasks,
                       SubtaskRepository subtasks,
                       CategoryRepository categories,
                       TagRepository tags,
                       TaskTagRepository taskTags,
                       GoalRepository goals,
                       ReminderSyncQueueService reminderSyncQueue) {
        this.tasks = tasks;
        this.subtasks = subtasks;
        this.categories = categories;
        this.tags = tags;
        this.taskTags = taskTags;
        this.goals = goals;
        this.reminderSyncQueue = reminderSyncQueue;
    }

    @Transactional
    public TaskResponse create(UUID userId, CreateTaskRequest request, String authorization) {
        validateDates(request.startAt(), request.dueAt());
        validateOwned(userId, request.categoryId(), request.goalId(), request.tagIds());

        Task task = new Task();
        task.setUserId(userId);
        task.setTitle(request.title().trim());
        task.setDescription(request.description());
        task.setStatus(request.status() == null ? TaskStatus.TODO : request.status());
        task.setPriority(request.priority() == null ? TaskPriority.MEDIUM : request.priority());
        task.setCategoryId(request.categoryId());
        task.setGoalId(request.goalId());
        task.setStartAt(request.startAt());
        task.setDueAt(request.dueAt());
        task.setEstimatedMinutes(request.estimatedMinutes());
        task.setActualMinutes(request.actualMinutes());
        task.setReminderMinutesBefore(request.reminderMinutesBefore());
        task.setRecurrenceRule(request.recurrenceRule());
        task.setPosition(0);
        syncCompleted(task, task.getStatus());
        tasks.save(task);

        replaceTags(task.getId(), request.tagIds());
        if (request.subtasks() != null) {
            int position = 0;
            for (SubtaskInput input : request.subtasks()) {
                Subtask subtask = new Subtask();
                subtask.setTaskId(task.getId());
                subtask.setTitle(input.title().trim());
                subtask.setPosition(position++);
                subtasks.save(subtask);
            }
        }
        completeSubtasksWhenTaskCompleted(task);
        syncReminderForTask(task);
        return response(task);
    }

    @Transactional(readOnly = true)
    public TaskResponse get(UUID userId, UUID id) {
        return response(owned(userId, id));
    }

    @Transactional
    public TaskResponse update(UUID userId, UUID id, UpdateTaskRequest request, String authorization) {
        validateDates(request.startAt(), request.dueAt());
        validateOwned(userId, request.categoryId(), request.goalId(), request.tagIds());

        Task task = owned(userId, id);
        task.setTitle(request.title().trim());
        task.setDescription(request.description());
        task.setStatus(request.status());
        task.setPriority(request.priority());
        task.setCategoryId(request.categoryId());
        task.setGoalId(request.goalId());
        task.setStartAt(request.startAt());
        task.setDueAt(request.dueAt());
        task.setEstimatedMinutes(request.estimatedMinutes());
        task.setActualMinutes(request.actualMinutes());
        task.setReminderMinutesBefore(request.reminderMinutesBefore());
        task.setRecurrenceRule(request.recurrenceRule());
        syncCompleted(task, request.status());
        completeSubtasksWhenTaskCompleted(task);
        replaceTags(id, request.tagIds());
        syncReminderForTask(task);
        return response(task);
    }

    @Transactional
    public void delete(UUID userId, UUID id, String authorization) {
        Task task = owned(userId, id);
        task.setDeletedAt(Instant.now());
        reminderSyncQueue.enqueueDelete(userId, id);
    }

    @Transactional
    public TaskResponse status(UUID userId, UUID id, TaskStatus status, String authorization) {
        Task task = owned(userId, id);
        task.setStatus(status);
        syncCompleted(task, status);
        completeSubtasksWhenTaskCompleted(task);
        if (isTerminal(status)) {
            reminderSyncQueue.enqueueDelete(userId, id);
        } else {
            syncReminderForTask(task);
        }
        return response(task);
    }

    @Transactional
    public List<TaskResponse> reorder(UUID userId, ReorderRequest request, String authorization) {
        List<TaskResponse> result = new ArrayList<>();
        for (ReorderItem item : request.items()) {
            Task task = owned(userId, item.taskId());
            task.setStatus(item.status());
            task.setPosition(item.position());
            syncCompleted(task, item.status());
            completeSubtasksWhenTaskCompleted(task);
            if (isTerminal(item.status())) {
                reminderSyncQueue.enqueueDelete(userId, task.getId());
            } else {
                syncReminderForTask(task);
            }
            result.add(response(task));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> list(UUID userId,
                                           String keyword,
                                           TaskStatus status,
                                           TaskPriority priority,
                                           UUID categoryId,
                                           UUID tagId,
                                           Instant from,
                                           Instant to,
                                           int page,
                                           int size,
                                           String sort) {
        if (page < 0 || size < 1) {
            throw new TaskApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION", "Trang và kích thước trang không hợp lệ");
        }
        int safeSize = Math.min(size, 100);
        Sort ordering = "dueAt,desc".equalsIgnoreCase(sort)
                ? Sort.by(Sort.Direction.DESC, "dueAt")
                : Sort.by(Sort.Direction.ASC, "dueAt");

        Set<UUID> taggedTaskIds = null;
        if (tagId != null) {
            tags.findByIdAndUserId(tagId, userId)
                    .orElseThrow(() -> notFound("TAG_NOT_FOUND", "Không tìm thấy nhãn"));
            Set<UUID> ids = new HashSet<>();
            for (TaskTag mapping : taskTags.findAllByTagId(tagId)) {
                ids.add(mapping.getTaskId());
            }
            taggedTaskIds = ids;
        }
        Set<UUID> finalTaggedTaskIds = taggedTaskIds;

        Specification<Task> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("userId"), userId));
            predicates.add(builder.isNull(root.get("deletedAt")));
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(builder.like(builder.lower(root.get("title")), "%" + keyword.trim().toLowerCase() + "%"));
            }
            if (status != null) predicates.add(builder.equal(root.get("status"), status));
            if (priority != null) predicates.add(builder.equal(root.get("priority"), priority));
            if (categoryId != null) predicates.add(builder.equal(root.get("categoryId"), categoryId));
            if (from != null) predicates.add(builder.greaterThanOrEqualTo(root.get("dueAt"), from));
            if (to != null) predicates.add(builder.lessThanOrEqualTo(root.get("dueAt"), to));
            if (finalTaggedTaskIds != null) predicates.add(root.get("id").in(finalTaggedTaskIds));
            return builder.and(predicates.toArray(Predicate[]::new));
        };

        Page<Task> result = tasks.findAll(specification, PageRequest.of(page, safeSize, ordering));
        return new PageResponse<>(result.stream().map(this::response).toList(), page, safeSize,
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> today(UUID userId, ZoneId zone) {
        LocalDate date = LocalDate.now(zone);
        return range(userId, date.atStartOfDay(zone).toInstant(), date.plusDays(1).atStartOfDay(zone).toInstant());
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> upcoming(UUID userId, ZoneId zone, int days) {
        LocalDate date = LocalDate.now(zone);
        return range(userId, date.plusDays(1).atStartOfDay(zone).toInstant(),
                date.plusDays(Math.max(1, days) + 1L).atStartOfDay(zone).toInstant());
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> overdue(UUID userId) {
        return tasks.findAllByUserIdAndDeletedAtIsNullAndDueAtBeforeAndStatusNotInOrderByDueAtAsc(
                        userId, Instant.now(), List.of(TaskStatus.COMPLETED, TaskStatus.CANCELLED))
                .stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(UUID userId, ZoneId zone) {
        long total = tasks.countByUserIdAndDeletedAtIsNull(userId);
        long todo = count(userId, TaskStatus.TODO);
        long inProgress = count(userId, TaskStatus.IN_PROGRESS);
        long completed = count(userId, TaskStatus.COMPLETED);
        long cancelled = count(userId, TaskStatus.CANCELLED);
        long overdue = tasks.countByUserIdAndDeletedAtIsNullAndDueAtBeforeAndStatusNotIn(
                userId, Instant.now(), List.of(TaskStatus.COMPLETED, TaskStatus.CANCELLED));

        LocalDate date = LocalDate.now(zone);
        Instant todayStart = date.atStartOfDay(zone).toInstant();
        Instant todayEnd = date.plusDays(1).atStartOfDay(zone).toInstant();
        long dueToday = tasks.findAllByUserIdAndDeletedAtIsNullAndDueAtBetweenOrderByDueAtAsc(
                userId, todayStart, todayEnd).size();

        LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Instant weekStart = monday.atStartOfDay(zone).toInstant();
        Instant weekEnd = monday.plusDays(7).atStartOfDay(zone).toInstant();
        long weekTotal = range(userId, weekStart, weekEnd).size();
        long weekCompleted = tasks.countByUserIdAndDeletedAtIsNullAndStatusAndDueAtBetween(
                userId, TaskStatus.COMPLETED, weekStart, weekEnd);
        double rate = weekTotal == 0 ? 0 : Math.round(weekCompleted * 1000.0 / weekTotal) / 10.0;

        return new DashboardResponse(total, todo, inProgress, completed, cancelled, overdue, dueToday, rate);
    }

    private long count(UUID userId, TaskStatus status) {
        return tasks.countByUserIdAndDeletedAtIsNullAndStatus(userId, status);
    }

    private List<TaskResponse> range(UUID userId, Instant from, Instant to) {
        return tasks.findAllByUserIdAndDeletedAtIsNullAndDueAtBetweenOrderByDueAtAsc(userId, from, to)
                .stream().map(this::response).toList();
    }

    private void validateDates(Instant start, Instant due) {
        if (start != null && due != null && due.isBefore(start)) {
            throw new TaskApiException(HttpStatus.BAD_REQUEST, "INVALID_TASK_DATE",
                    "Deadline không được trước thời gian bắt đầu");
        }
    }

    private void validateOwned(UUID userId, UUID categoryId, UUID goalId, Set<UUID> tagIds) {
        if (categoryId != null) {
            categories.findByIdAndUserId(categoryId, userId)
                    .orElseThrow(() -> notFound("CATEGORY_NOT_FOUND", "Không tìm thấy danh mục"));
        }
        if (goalId != null) {
            goals.findByIdAndUserId(goalId, userId)
                    .orElseThrow(() -> notFound("GOAL_NOT_FOUND", "Không tìm thấy mục tiêu"));
        }
        if (tagIds != null) {
            tagIds.forEach(id -> tags.findByIdAndUserId(id, userId)
                    .orElseThrow(() -> notFound("TAG_NOT_FOUND", "Không tìm thấy nhãn")));
        }
    }

    private Task owned(UUID userId, UUID id) {
        return tasks.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> notFound("TASK_NOT_FOUND", "Không tìm thấy công việc"));
    }

    private TaskApiException notFound(String code, String message) {
        return new TaskApiException(HttpStatus.NOT_FOUND, code, message);
    }

    private void syncCompleted(Task task, TaskStatus status) {
        if (status == TaskStatus.COMPLETED && task.getCompletedAt() == null) task.setCompletedAt(Instant.now());
        if (status != TaskStatus.COMPLETED) task.setCompletedAt(null);
    }

    private void completeSubtasksWhenTaskCompleted(Task task) {
        if (task.getStatus() != TaskStatus.COMPLETED || task.getId() == null) return;
        Instant completedAt = Instant.now();
        List<Subtask> values = subtasks.findAllByTaskIdOrderByPositionAsc(task.getId());
        values.stream().filter(value -> !value.isCompleted()).forEach(value -> {
            value.setCompleted(true);
            value.setCompletedAt(completedAt);
        });
        if (!values.isEmpty()) subtasks.saveAll(values);
    }

    private boolean isTerminal(TaskStatus status) {
        return status == TaskStatus.COMPLETED || status == TaskStatus.CANCELLED;
    }

    private void replaceTags(UUID taskId, Set<UUID> ids) {
        taskTags.deleteAllByTaskId(taskId);
        taskTags.flush();
        if (ids == null || ids.isEmpty()) return;
        Set<UUID> uniqueIds = new LinkedHashSet<>(ids);
        List<TaskTag> mappings = uniqueIds.stream().map(id -> {
            TaskTag mapping = new TaskTag();
            mapping.setTaskId(taskId);
            mapping.setTagId(id);
            return mapping;
        }).toList();
        taskTags.saveAll(mappings);
        taskTags.flush();
    }

    private void syncReminderForTask(Task task) {
        Integer minutesBefore = task.getReminderMinutesBefore();
        if (isTerminal(task.getStatus()) || task.getDueAt() == null || minutesBefore == null) {
            reminderSyncQueue.enqueueDelete(task.getUserId(), task.getId());
            return;
        }
        reminderSyncQueue.enqueueUpsert(task.getUserId(), task.getId(),
                task.getDueAt().minusSeconds(minutesBefore * 60L));
    }

    private TaskResponse response(Task task) {
        List<SubtaskResponse> subtaskResponses = subtasks.findAllByTaskIdOrderByPositionAsc(task.getId())
                .stream()
                .map(value -> new SubtaskResponse(value.getId(), value.getTitle(), value.isCompleted(),
                        value.getPosition(), value.getCompletedAt()))
                .toList();
        List<TagResponse> tagResponses = taskTags.findAllByTaskId(task.getId())
                .stream()
                .map(TaskTag::getTagId)
                .map(tags::findById)
                .flatMap(Optional::stream)
                .map(value -> new TagResponse(value.getId(), value.getName(), value.getColor()))
                .toList();
        return new TaskResponse(task.getId(), task.getTitle(), task.getDescription(), task.getStatus(),
                task.getPriority(), task.getCategoryId(), task.getGoalId(), task.getStartAt(), task.getDueAt(),
                task.getEstimatedMinutes(), task.getActualMinutes(), task.getReminderMinutesBefore(),
                task.getPosition(), task.getRecurrenceRule(),
                task.getCompletedAt(), task.getVersion(), subtaskResponses, tagResponses,
                task.getCreatedAt(), task.getUpdatedAt());
    }
}
