package com.smarttask.task.controller;
import com.smarttask.task.domain.*; import com.smarttask.task.dto.TaskDtos.*; import com.smarttask.task.service.TaskService; import jakarta.validation.Valid; import org.springframework.http.*; import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*; import java.time.*; import java.util.*;
@RestController @RequestMapping("/api/v1/tasks")
public class TaskController{
 private final TaskService service; public TaskController(TaskService service){this.service=service;}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public TaskResponse create(Authentication a,@RequestHeader(HttpHeaders.AUTHORIZATION)String authorization,@Valid @RequestBody CreateTaskRequest r){return service.create(uid(a),r,authorization);}
 @GetMapping public PageResponse<TaskResponse> list(Authentication a,@RequestParam(required=false)String keyword,@RequestParam(required=false)TaskStatus status,@RequestParam(required=false)TaskPriority priority,@RequestParam(required=false)UUID categoryId,@RequestParam(required=false)UUID tagId,@RequestParam(required=false)Instant from,@RequestParam(required=false)Instant to,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,@RequestParam(defaultValue="dueAt,asc")String sort){return service.list(uid(a),keyword,status,priority,categoryId,tagId,from,to,page,size,sort);}
 @GetMapping("/{id}") public TaskResponse get(Authentication a,@PathVariable UUID id){return service.get(uid(a),id);}
 @PutMapping("/{id}") public TaskResponse update(Authentication a,@PathVariable UUID id,@RequestHeader(HttpHeaders.AUTHORIZATION)String authorization,@Valid @RequestBody UpdateTaskRequest r){return service.update(uid(a),id,r,authorization);}
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(Authentication a,@PathVariable UUID id,@RequestHeader(HttpHeaders.AUTHORIZATION)String authorization){service.delete(uid(a),id,authorization);}
 @PatchMapping("/{id}/status") public TaskResponse status(Authentication a,@PathVariable UUID id,@RequestHeader(HttpHeaders.AUTHORIZATION)String authorization,@Valid @RequestBody ChangeStatusRequest r){return service.status(uid(a),id,r.status(),authorization);}
 @PatchMapping("/{id}/complete") public TaskResponse complete(Authentication a,@PathVariable UUID id,@RequestHeader(HttpHeaders.AUTHORIZATION)String authorization){return service.status(uid(a),id,TaskStatus.COMPLETED,authorization);}
 @PatchMapping("/reorder") public List<TaskResponse> reorder(Authentication a,@RequestHeader(HttpHeaders.AUTHORIZATION)String authorization,@Valid @RequestBody ReorderRequest r){return service.reorder(uid(a),r,authorization);}
 @GetMapping("/today") public List<TaskResponse> today(Authentication a,@RequestParam(defaultValue="Asia/Bangkok")String timezone){return service.today(uid(a),ZoneId.of(timezone));}
 @GetMapping("/upcoming") public List<TaskResponse> upcoming(Authentication a,@RequestParam(defaultValue="Asia/Bangkok")String timezone,@RequestParam(defaultValue="7")int days){return service.upcoming(uid(a),ZoneId.of(timezone),days);}
 @GetMapping("/overdue") public List<TaskResponse> overdue(Authentication a){return service.overdue(uid(a));}
 private UUID uid(Authentication a){return UUID.fromString(a.getName());}
}
