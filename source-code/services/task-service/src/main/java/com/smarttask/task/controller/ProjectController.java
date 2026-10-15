package com.smarttask.task.controller;

import com.smarttask.task.domain.TaskStatus;
import com.smarttask.task.dto.ProjectDtos.*;
import com.smarttask.task.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {
    private final ProjectService service;

    public ProjectController(ProjectService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProjectResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(uid(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(@AuthenticationPrincipal Jwt jwt,
                                  @Valid @RequestBody CreateProjectRequest request) {
        return service.create(uid(jwt), email(jwt), request);
    }

    @PostMapping("/invitations/accept")
    public ProjectResponse accept(@AuthenticationPrincipal Jwt jwt,
                                  @Valid @RequestBody AcceptInvitationRequest request) {
        return service.accept(uid(jwt), email(jwt), request);
    }

    @GetMapping("/invitations")
    public List<InvitationResponse> invitations(@AuthenticationPrincipal Jwt jwt) {
        return service.invitations(email(jwt));
    }

    @GetMapping("/{projectId}")
    public ProjectResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return service.get(uid(jwt), projectId);
    }

    @PutMapping("/{projectId}")
    public ProjectResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                                  @Valid @RequestBody UpdateProjectRequest request) {
        return service.update(uid(jwt), projectId, request);
    }

    @GetMapping("/{projectId}/members")
    public List<MemberResponse> members(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return service.members(uid(jwt), projectId);
    }

    @DeleteMapping("/{projectId}/members/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        service.leave(uid(jwt), projectId);
    }

    @DeleteMapping("/{projectId}/members/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                             @PathVariable UUID memberId) {
        service.removeMember(uid(jwt), projectId, memberId);
    }

    @PostMapping("/{projectId}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationResponse invite(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                                     @Valid @RequestBody InviteMemberRequest request) {
        return service.invite(uid(jwt), projectId, request);
    }

    @GetMapping("/{projectId}/tasks")
    public ProjectTaskPage tasks(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                                 @RequestParam(required = false) TaskStatus status,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "20") int size) {
        return service.tasks(uid(jwt), projectId, status, page, size);
    }

    @PostMapping("/{projectId}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectTaskResponse createTask(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                                          @Valid @RequestBody CreateProjectTaskRequest request) {
        return service.createTask(uid(jwt), projectId, request);
    }

    @GetMapping("/{projectId}/tasks/{taskId}")
    public ProjectTaskResponse task(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                                    @PathVariable UUID taskId) {
        return service.task(uid(jwt), projectId, taskId);
    }

    @PutMapping("/{projectId}/tasks/{taskId}")
    public ProjectTaskResponse updateTask(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                                          @PathVariable UUID taskId,
                                          @Valid @RequestBody UpdateProjectTaskRequest request) {
        return service.updateTask(uid(jwt), projectId, taskId, request);
    }

    @PatchMapping("/{projectId}/tasks/{taskId}/status")
    public ProjectTaskResponse changeStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                                            @PathVariable UUID taskId,
                                            @Valid @RequestBody ChangeProjectTaskStatusRequest request) {
        return service.changeStatus(uid(jwt), projectId, taskId, request);
    }

    @DeleteMapping("/{projectId}/tasks/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTask(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                           @PathVariable UUID taskId) {
        service.deleteTask(uid(jwt), projectId, taskId);
    }

    private UUID uid(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private String email(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
