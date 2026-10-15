package com.smarttask.task.controller;

import com.smarttask.task.dto.TaskDtos.*; import com.smarttask.task.service.SupportingService; import jakarta.validation.Valid; import org.springframework.http.*; import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*; import java.util.*;

public final class SupportingControllers{private SupportingControllers(){}
 public static UUID uid(Authentication a){return UUID.fromString(a.getName());}
}

@RestController @RequestMapping("/api/v1/categories")
class CategoryController{private final SupportingService s;CategoryController(SupportingService s){this.s=s;}@GetMapping List<CategoryResponse> all(Authentication a){return s.categories(SupportingControllers.uid(a));}@PostMapping @ResponseStatus(HttpStatus.CREATED) CategoryResponse create(Authentication a,@Valid @RequestBody CategoryRequest r){return s.createCategory(SupportingControllers.uid(a),r);}@PutMapping("/{id}") CategoryResponse update(Authentication a,@PathVariable UUID id,@Valid @RequestBody CategoryRequest r){return s.updateCategory(SupportingControllers.uid(a),id,r);}@DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void delete(Authentication a,@PathVariable UUID id){s.deleteCategory(SupportingControllers.uid(a),id);}}

@RestController @RequestMapping("/api/v1/tags")
class TagController{private final SupportingService s;TagController(SupportingService s){this.s=s;}@GetMapping List<TagResponse> all(Authentication a){return s.tags(SupportingControllers.uid(a));}@PostMapping @ResponseStatus(HttpStatus.CREATED) TagResponse create(Authentication a,@Valid @RequestBody TagRequest r){return s.createTag(SupportingControllers.uid(a),r);}@PutMapping("/{id}") TagResponse update(Authentication a,@PathVariable UUID id,@Valid @RequestBody TagRequest r){return s.updateTag(SupportingControllers.uid(a),id,r);}@DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void delete(Authentication a,@PathVariable UUID id){s.deleteTag(SupportingControllers.uid(a),id);}}

@RestController @RequestMapping("/api/v1/goals")
class GoalController{private final SupportingService s;GoalController(SupportingService s){this.s=s;}@GetMapping List<GoalResponse> all(Authentication a){return s.goals(SupportingControllers.uid(a));}@PostMapping @ResponseStatus(HttpStatus.CREATED) GoalResponse create(Authentication a,@Valid @RequestBody GoalRequest r){return s.createGoal(SupportingControllers.uid(a),r);}@PutMapping("/{id}") GoalResponse update(Authentication a,@PathVariable UUID id,@Valid @RequestBody GoalRequest r){return s.updateGoal(SupportingControllers.uid(a),id,r);}@DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void delete(Authentication a,@PathVariable UUID id){s.deleteGoal(SupportingControllers.uid(a),id);}}

@RestController @RequestMapping("/api/v1/tasks/{taskId}/subtasks")
class SubtaskController{private final SupportingService s;SubtaskController(SupportingService s){this.s=s;}@PostMapping @ResponseStatus(HttpStatus.CREATED) SubtaskResponse create(Authentication a,@PathVariable UUID taskId,@Valid @RequestBody SubtaskRequest r){return s.addSubtask(SupportingControllers.uid(a),taskId,r);}@PutMapping("/{id}") SubtaskResponse update(Authentication a,@PathVariable UUID taskId,@PathVariable UUID id,@Valid @RequestBody SubtaskRequest r){return s.updateSubtask(SupportingControllers.uid(a),taskId,id,r);}@PatchMapping("/{id}/complete") SubtaskResponse complete(Authentication a,@PathVariable UUID taskId,@PathVariable UUID id){return s.completeSubtask(SupportingControllers.uid(a),taskId,id);}@DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void delete(Authentication a,@PathVariable UUID taskId,@PathVariable UUID id){s.deleteSubtask(SupportingControllers.uid(a),taskId,id);}}

