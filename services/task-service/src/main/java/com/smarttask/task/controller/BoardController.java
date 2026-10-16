package com.smarttask.task.controller;

import com.smarttask.task.dto.BoardDtos.*;
import com.smarttask.task.dto.ProjectDtos.UpdateProjectTaskRequest;
import com.smarttask.task.service.BoardService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/boards")
public class BoardController {
    private final BoardService service;

    public BoardController(BoardService service) {
        this.service = service;
    }

    @GetMapping
    public List<BoardSummary> boards(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return service.boards(uid(jwt), projectId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BoardSummary create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                               @Valid @RequestBody CreateBoardRequest request) {
        return service.create(uid(jwt), projectId, request);
    }

    @GetMapping("/{boardId}")
    public BoardDetails board(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                              @PathVariable UUID boardId) {
        return service.board(uid(jwt), projectId, boardId);
    }

    @PutMapping("/{boardId}")
    public BoardSummary update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                               @PathVariable UUID boardId, @Valid @RequestBody UpdateBoardRequest request) {
        return service.update(uid(jwt), projectId, boardId, request);
    }

    @PostMapping("/{boardId}/copy")
    @ResponseStatus(HttpStatus.CREATED)
    public BoardSummary copy(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                             @PathVariable UUID boardId, @Valid @RequestBody CopyBoardRequest request) {
        return service.copyBoard(uid(jwt), projectId, boardId, request);
    }

    @PostMapping("/{boardId}/lists")
    @ResponseStatus(HttpStatus.CREATED)
    public BoardList addList(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                             @PathVariable UUID boardId, @Valid @RequestBody ListRequest request) {
        return service.addList(uid(jwt), projectId, boardId, request);
    }

    @PutMapping("/{boardId}/lists/{listId}")
    public BoardList renameList(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                                @PathVariable UUID boardId, @PathVariable UUID listId,
                                @Valid @RequestBody ListRequest request) {
        return service.renameList(uid(jwt), projectId, boardId, listId, request);
    }

    @PutMapping("/{boardId}/lists/order")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reorderLists(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                             @PathVariable UUID boardId, @Valid @RequestBody ReorderListsRequest request) {
        service.reorderLists(uid(jwt), projectId, boardId, request);
    }

    @DeleteMapping("/{boardId}/lists/{listId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archiveList(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                            @PathVariable UUID boardId, @PathVariable UUID listId) {
        service.archiveList(uid(jwt), projectId, boardId, listId);
    }

    @PostMapping("/{boardId}/lists/{listId}/cards")
    @ResponseStatus(HttpStatus.CREATED)
    public BoardCard createCard(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                                @PathVariable UUID boardId, @PathVariable UUID listId,
                                @Valid @RequestBody CreateCardRequest request) {
        return service.createCard(uid(jwt), projectId, boardId, listId, request);
    }

    @PatchMapping("/{boardId}/cards/{taskId}/move")
    public BoardCard moveCard(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                              @PathVariable UUID boardId, @PathVariable UUID taskId,
                              @Valid @RequestBody MoveCardRequest request) {
        return service.moveCard(uid(jwt), projectId, boardId, taskId, request);
    }

    @PutMapping("/{boardId}/cards/{taskId}")
    public BoardCard updateCard(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                                @PathVariable UUID boardId, @PathVariable UUID taskId,
                                @Valid @RequestBody UpdateProjectTaskRequest request) {
        return service.updateCard(uid(jwt), projectId, boardId, taskId, request);
    }

    @PostMapping("/{boardId}/cards/{taskId}/copy")
    @ResponseStatus(HttpStatus.CREATED)
    public BoardCard copyCard(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                              @PathVariable UUID boardId, @PathVariable UUID taskId,
                              @Valid @RequestBody CopyCardRequest request) {
        return service.copyCard(uid(jwt), projectId, boardId, taskId, request);
    }

    @PostMapping("/{boardId}/cards/{taskId}/transfer")
    public BoardCard transferCard(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                                  @PathVariable UUID boardId, @PathVariable UUID taskId,
                                  @Valid @RequestBody TransferCardRequest request) {
        return service.transferCard(uid(jwt), projectId, boardId, taskId, request);
    }

    @DeleteMapping("/{boardId}/cards/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archiveCard(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                            @PathVariable UUID boardId, @PathVariable UUID taskId) {
        service.archiveCard(uid(jwt), projectId, boardId, taskId);
    }

    @GetMapping("/{boardId}/archived-cards")
    public List<BoardCard> archivedCards(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                                         @PathVariable UUID boardId) {
        return service.archivedCards(uid(jwt), projectId, boardId);
    }

    @PostMapping("/{boardId}/archived-cards/{taskId}/restore")
    public BoardCard restoreCard(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                                 @PathVariable UUID boardId, @PathVariable UUID taskId) {
        return service.restoreCard(uid(jwt), projectId, boardId, taskId);
    }

    private UUID uid(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
