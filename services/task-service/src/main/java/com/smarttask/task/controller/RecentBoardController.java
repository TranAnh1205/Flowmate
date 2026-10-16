package com.smarttask.task.controller;

import com.smarttask.task.dto.BoardDtos.RecentBoard;
import com.smarttask.task.service.BoardService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
public class RecentBoardController {
    private final BoardService boards;

    public RecentBoardController(BoardService boards) {
        this.boards = boards;
    }

    @GetMapping("/recent-boards")
    public List<RecentBoard> recent(@AuthenticationPrincipal Jwt jwt) {
        return boards.recent(UUID.fromString(jwt.getSubject()));
    }

    @PostMapping("/{projectId}/boards/{boardId}/view")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void view(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                     @PathVariable UUID boardId) {
        boards.recordView(UUID.fromString(jwt.getSubject()), projectId, boardId);
    }
}
