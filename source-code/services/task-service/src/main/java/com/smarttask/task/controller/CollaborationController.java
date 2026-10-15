package com.smarttask.task.controller;

import com.smarttask.task.dto.CollaborationDtos.*;
import com.smarttask.task.service.CollaborationService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/boards/{boardId}")
public class CollaborationController {
    private final CollaborationService service;

    public CollaborationController(CollaborationService service) {
        this.service = service;
    }

    @GetMapping("/cards/{taskId}/comments")
    public List<Comment> comments(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                                  @PathVariable UUID boardId, @PathVariable UUID taskId) {
        return service.comments(uid(jwt), projectId, boardId, taskId);
    }

    @PostMapping("/cards/{taskId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public Comment addComment(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                              @PathVariable UUID boardId, @PathVariable UUID taskId,
                              @Valid @RequestBody TextRequest request) {
        return service.addComment(uid(jwt), projectId, boardId, taskId, request);
    }

    @DeleteMapping("/cards/{taskId}/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                              @PathVariable UUID boardId, @PathVariable UUID taskId,
                              @PathVariable UUID commentId) {
        service.deleteComment(uid(jwt), projectId, boardId, taskId, commentId);
    }

    @GetMapping("/cards/{taskId}/checklist")
    public List<ChecklistItem> checklist(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                                         @PathVariable UUID boardId, @PathVariable UUID taskId) {
        return service.checklist(uid(jwt), projectId, boardId, taskId);
    }

    @PostMapping("/cards/{taskId}/checklist")
    @ResponseStatus(HttpStatus.CREATED)
    public ChecklistItem addChecklist(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                                      @PathVariable UUID boardId, @PathVariable UUID taskId,
                                      @Valid @RequestBody ChecklistRequest request) {
        return service.addChecklist(uid(jwt), projectId, boardId, taskId, request);
    }

    @PutMapping("/cards/{taskId}/checklist/{itemId}")
    public ChecklistItem updateChecklist(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                                         @PathVariable UUID boardId, @PathVariable UUID taskId,
                                         @PathVariable UUID itemId,
                                         @Valid @RequestBody ChecklistUpdateRequest request) {
        return service.updateChecklist(uid(jwt), projectId, boardId, taskId, itemId, request);
    }

    @DeleteMapping("/cards/{taskId}/checklist/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteChecklist(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                                @PathVariable UUID boardId, @PathVariable UUID taskId,
                                @PathVariable UUID itemId) {
        service.deleteChecklist(uid(jwt), projectId, boardId, taskId, itemId);
    }

    @GetMapping("/cards/{taskId}/attachments")
    public List<Attachment> attachments(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                                        @PathVariable UUID boardId, @PathVariable UUID taskId) {
        return service.attachments(uid(jwt), projectId, boardId, taskId);
    }

    @PostMapping(value = "/cards/{taskId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Attachment upload(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                             @PathVariable UUID boardId, @PathVariable UUID taskId,
                             @RequestParam("file") MultipartFile file) {
        return service.upload(uid(jwt), projectId, boardId, taskId, file);
    }

    @GetMapping("/cards/{taskId}/attachments/{attachmentId}")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                                           @PathVariable UUID boardId, @PathVariable UUID taskId,
                                           @PathVariable UUID attachmentId) {
        var file = service.download(uid(jwt), projectId, boardId, taskId, attachmentId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.filename(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(file.bytes());
    }

    @DeleteMapping("/cards/{taskId}/attachments/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAttachment(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                                 @PathVariable UUID boardId, @PathVariable UUID taskId,
                                 @PathVariable UUID attachmentId) {
        service.deleteAttachment(uid(jwt), projectId, boardId, taskId, attachmentId);
    }

    @GetMapping("/messages")
    public List<Message> messages(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                                  @PathVariable UUID boardId,
                                  @RequestParam(required = false) Instant before,
                                  @RequestParam(defaultValue = "50") int limit) {
        return service.messages(uid(jwt), projectId, boardId, before, limit);
    }

    @PostMapping("/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public Message addMessage(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                              @PathVariable UUID boardId, @Valid @RequestBody TextRequest request) {
        return service.addMessage(uid(jwt), projectId, boardId, request);
    }

    @GetMapping("/events")
    public List<Event> events(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                              @PathVariable UUID boardId, @RequestParam(defaultValue = "0") long afterId) {
        return service.events(uid(jwt), projectId, boardId, afterId);
    }

    @GetMapping("/labels")
    public List<Label> labels(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                              @PathVariable UUID boardId) {
        return service.labels(uid(jwt), projectId, boardId);
    }

    @PostMapping("/labels")
    @ResponseStatus(HttpStatus.CREATED)
    public Label addLabel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                          @PathVariable UUID boardId, @Valid @RequestBody LabelRequest request) {
        return service.addLabel(uid(jwt), projectId, boardId, request);
    }

    @GetMapping("/cards/{taskId}/labels")
    public List<Label> cardLabels(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                                  @PathVariable UUID boardId, @PathVariable UUID taskId) {
        return service.cardLabels(uid(jwt), projectId, boardId, taskId);
    }

    @PostMapping("/cards/{taskId}/labels/{labelId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void attachLabel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                            @PathVariable UUID boardId, @PathVariable UUID taskId,
                            @PathVariable UUID labelId) {
        service.attachLabel(uid(jwt), projectId, boardId, taskId, labelId);
    }

    @GetMapping("/cards/{taskId}/watchers")
    public List<UUID> watchers(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                               @PathVariable UUID boardId, @PathVariable UUID taskId) {
        return service.watchers(uid(jwt), projectId, boardId, taskId);
    }

    @PutMapping("/cards/{taskId}/watchers/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void watch(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                      @PathVariable UUID boardId, @PathVariable UUID taskId) {
        service.watch(uid(jwt), projectId, boardId, taskId, true);
    }

    @DeleteMapping("/cards/{taskId}/watchers/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unwatch(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                        @PathVariable UUID boardId, @PathVariable UUID taskId) {
        service.watch(uid(jwt), projectId, boardId, taskId, false);
    }

    private UUID uid(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
