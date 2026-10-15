package com.smarttask.notification.controller;
import com.smarttask.notification.dto.NotificationDtos.*;import com.smarttask.notification.service.NotificationService;import jakarta.validation.Valid;import org.springframework.http.*;import org.springframework.scheduling.annotation.Scheduled;import org.springframework.security.core.Authentication;import org.springframework.web.bind.annotation.*;import java.util.*;
@RestController
public class NotificationController{
 private final NotificationService s;public NotificationController(NotificationService s){this.s=s;}
 @GetMapping("/api/v1/notifications")public PageResponse<NotificationResponse>list(Authentication a,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return s.list(uid(a),page,size);}
 @GetMapping("/api/v1/notifications/unread-count")public UnreadCountResponse unread(Authentication a){return s.unread(uid(a));}
 @PatchMapping("/api/v1/notifications/{id}/read")public NotificationResponse read(Authentication a,@PathVariable UUID id){return s.read(uid(a),id);}
 @PatchMapping("/api/v1/notifications/read-all")public Map<String,Integer>readAll(Authentication a){return Map.of("updated",s.readAll(uid(a)));}
 @PostMapping("/api/v1/reminders")@ResponseStatus(HttpStatus.CREATED)public ReminderResponse create(Authentication a,@Valid @RequestBody ReminderRequest r){return s.createReminder(uid(a),r);}
 @PutMapping("/api/v1/reminders/task/{taskId}")public ReminderResponse sync(Authentication a,@PathVariable UUID taskId,@Valid @RequestBody ReminderSyncRequest r){return s.syncReminder(uid(a),taskId,r);}
 @DeleteMapping("/api/v1/reminders/task/{taskId}")@ResponseStatus(HttpStatus.NO_CONTENT)public void deleteForTask(Authentication a,@PathVariable UUID taskId){s.deleteTaskReminders(uid(a),taskId);}
 @PutMapping("/api/v1/reminders/{id}")public ReminderResponse update(Authentication a,@PathVariable UUID id,@Valid @RequestBody ReminderRequest r){return s.updateReminder(uid(a),id,r);}
 @DeleteMapping("/api/v1/reminders/{id}")@ResponseStatus(HttpStatus.NO_CONTENT)public void delete(Authentication a,@PathVariable UUID id){s.deleteReminder(uid(a),id);}
 private UUID uid(Authentication a){return UUID.fromString(a.getName());}
}
