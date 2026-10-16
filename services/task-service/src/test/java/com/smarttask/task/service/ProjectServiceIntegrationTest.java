package com.smarttask.task.service;

import com.smarttask.task.domain.TaskPriority;
import com.smarttask.task.domain.TaskStatus;
import com.smarttask.task.dto.ProjectDtos.*;
import com.smarttask.task.exception.TaskApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@JdbcTest
@Import(ProjectService.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:projects;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@Sql(scripts = {"classpath:db/migration/V3__add_team_projects.sql",
                "classpath:db/migration/V7__add_team_task_start_date.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class ProjectServiceIntegrationTest {
    @Autowired ProjectService projects;
    @Autowired JdbcTemplate jdbc;

    @Test
    void invitationMembershipTaskPermissionsAndOptimisticUpdate() {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();
        var project = projects.create(owner, "owner@example.com", new CreateProjectRequest("Đồ án", "Nhóm 2 người"));
        assertEquals("OWNER", project.myRole());
        assertEquals(1, project.memberCount());
        assertEquals("PROJECT_NOT_FOUND", assertThrows(TaskApiException.class,
                () -> projects.get(outsider, project.id())).code());

        var invitation = projects.invite(owner, project.id(), new InviteMemberRequest("MEMBER@example.com"));
        assertEquals("member@example.com", invitation.invitedEmail());
        assertEquals(1, projects.invitations("member@example.com").size());
        assertEquals("INVITATION_NOT_FOUND", assertThrows(TaskApiException.class,
                () -> projects.accept(outsider, "other@example.com", new AcceptInvitationRequest(invitation.id()))).code());
        assertEquals("MEMBER", projects.accept(member, "member@example.com",
                new AcceptInvitationRequest(invitation.id())).myRole());
        assertTrue(projects.invitations("member@example.com").isEmpty());
        assertEquals(2, projects.members(owner, project.id()).size());

        assertEquals("PROJECT_ACCESS_DENIED", assertThrows(TaskApiException.class,
                () -> projects.createTask(member, project.id(), new CreateProjectTaskRequest(
                        "Giao cho chủ", null, null, null, owner, null))).code());
        var task = projects.createTask(owner, project.id(), new CreateProjectTaskRequest(
                "Viết báo cáo", null, null, TaskPriority.HIGH, member, null));
        assertEquals(member, task.assigneeId());
        assertEquals(1, projects.tasks(member, project.id(), null, 0, 20).totalElements());
        assertEquals("PROJECT_NOT_FOUND", assertThrows(TaskApiException.class,
                () -> projects.tasks(outsider, project.id(), null, 0, 20)).code());

        var completed = projects.changeStatus(member, project.id(), task.id(),
                new ChangeProjectTaskStatusRequest(TaskStatus.COMPLETED, task.version()));
        assertNotNull(completed.completedAt());
        assertEquals("STALE_PROJECT_TASK", assertThrows(TaskApiException.class,
                () -> projects.changeStatus(owner, project.id(), task.id(),
                        new ChangeProjectTaskStatusRequest(TaskStatus.TODO, task.version()))).code());
        assertEquals(1, projects.get(owner, project.id()).completedTaskCount());

        var deletedTask = projects.createTask(owner, project.id(), new CreateProjectTaskRequest(
                "Việc đã xóa", null, null, null, member, null));
        projects.deleteTask(owner, project.id(), deletedTask.id());

        projects.removeMember(owner, project.id(), member);
        assertNull(projects.task(owner, project.id(), task.id()).assigneeId());
        assertEquals("PROJECT_NOT_FOUND", assertThrows(TaskApiException.class,
                () -> projects.get(member, project.id())).code());
    }

    @Test
    void invitationIsPrivateSingleUseAndExpires() {
        UUID owner = UUID.randomUUID();
        UUID invitee = UUID.randomUUID();
        var project = projects.create(owner, "OWNER@Example.com", new CreateProjectRequest("Nhóm", null));
        assertEquals("owner@example.com", projects.members(owner, project.id()).getFirst().email());
        assertEquals("ALREADY_MEMBER", assertThrows(TaskApiException.class,
                () -> projects.invite(owner, project.id(), new InviteMemberRequest("owner@example.com"))).code());
        var invitation = projects.invite(owner, project.id(), new InviteMemberRequest("Invitee@Example.com"));

        assertTrue(projects.invitations("other@example.com").isEmpty());
        assertEquals("INVITATION_NOT_FOUND", assertThrows(TaskApiException.class,
                () -> projects.accept(invitee, "other@example.com",
                        new AcceptInvitationRequest(invitation.id()))).code());
        assertEquals("INVITATION_PENDING", assertThrows(TaskApiException.class,
                () -> projects.invite(owner, project.id(), new InviteMemberRequest("invitee@example.com"))).code());

        projects.accept(invitee, "INVITEE@example.com", new AcceptInvitationRequest(invitation.id()));
        assertEquals("INVITATION_EXPIRED", assertThrows(TaskApiException.class,
                () -> projects.accept(UUID.randomUUID(), "invitee@example.com",
                        new AcceptInvitationRequest(invitation.id()))).code());
        assertEquals("ALREADY_MEMBER", assertThrows(TaskApiException.class,
                () -> projects.invite(owner, project.id(), new InviteMemberRequest("invitee@example.com"))).code());

        var expiring = projects.invite(owner, project.id(), new InviteMemberRequest("late@example.com"));
        jdbc.update("UPDATE team_project_invitations SET expires_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minusSeconds(1)), expiring.id());
        assertTrue(projects.invitations("late@example.com").isEmpty());
        assertEquals("INVITATION_EXPIRED", assertThrows(TaskApiException.class,
                () -> projects.accept(UUID.randomUUID(), "late@example.com",
                        new AcceptInvitationRequest(expiring.id()))).code());
    }

    @Test
    void archivedProjectIsReadableButRejectsTaskAndInvitationMutations() {
        UUID owner = UUID.randomUUID();
        var project = projects.create(owner, "owner@example.com", new CreateProjectRequest("Nhóm", null));
        var task = projects.createTask(owner, project.id(), new CreateProjectTaskRequest(
                "Việc đã có", null, null, null, null, null));
        projects.update(owner, project.id(), new UpdateProjectRequest("Nhóm", null, "ARCHIVED"));

        assertEquals("ARCHIVED", projects.get(owner, project.id()).status());
        assertEquals(task.id(), projects.task(owner, project.id(), task.id()).id());
        assertEquals(1, projects.tasks(owner, project.id(), null, 0, 20).totalElements());
        assertEquals("PROJECT_ARCHIVED", assertThrows(TaskApiException.class,
                () -> projects.invite(owner, project.id(), new InviteMemberRequest("new@example.com"))).code());
        assertEquals("PROJECT_ARCHIVED", assertThrows(TaskApiException.class,
                () -> projects.createTask(owner, project.id(), new CreateProjectTaskRequest(
                        "Việc mới", null, null, null, null, null))).code());
        assertEquals("PROJECT_ARCHIVED", assertThrows(TaskApiException.class,
                () -> projects.changeStatus(owner, project.id(), task.id(),
                        new ChangeProjectTaskStatusRequest(TaskStatus.COMPLETED, task.version()))).code());
        assertEquals("PROJECT_ARCHIVED", assertThrows(TaskApiException.class,
                () -> projects.deleteTask(owner, project.id(), task.id())).code());

        projects.update(owner, project.id(), new UpdateProjectRequest("Nhóm", null, "ACTIVE"));
        assertEquals(TaskStatus.COMPLETED, projects.changeStatus(owner, project.id(), task.id(),
                new ChangeProjectTaskStatusRequest(TaskStatus.COMPLETED, task.version())).status());
    }

    @Test
    void memberCannotReassignOrEditAnotherMembersTask() {
        UUID owner = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        var project = projects.create(owner, "owner@example.com", new CreateProjectRequest("Nhóm", null));
        var firstInvite = projects.invite(owner, project.id(), new InviteMemberRequest("first@example.com"));
        projects.accept(first, "first@example.com", new AcceptInvitationRequest(firstInvite.id()));
        var secondInvite = projects.invite(owner, project.id(), new InviteMemberRequest("second@example.com"));
        projects.accept(second, "second@example.com", new AcceptInvitationRequest(secondInvite.id()));

        var task = projects.createTask(owner, project.id(), new CreateProjectTaskRequest(
                "Giao người thứ nhất", null, null, null, first, null));
        assertEquals("PROJECT_ACCESS_DENIED", assertThrows(TaskApiException.class,
                () -> projects.changeStatus(second, project.id(), task.id(),
                        new ChangeProjectTaskStatusRequest(TaskStatus.COMPLETED, task.version()))).code());
        assertEquals("PROJECT_ACCESS_DENIED", assertThrows(TaskApiException.class,
                () -> projects.updateTask(first, project.id(), task.id(), new UpdateProjectTaskRequest(
                        task.title(), null, TaskStatus.IN_PROGRESS, TaskPriority.MEDIUM,
                        second, null, task.version()))).code());
        assertEquals("PROJECT_ACCESS_DENIED", assertThrows(TaskApiException.class,
                () -> projects.createTask(first, project.id(), new CreateProjectTaskRequest(
                        "Giao người khác", null, null, null, second, null))).code());
        assertEquals("INVALID_ASSIGNEE", assertThrows(TaskApiException.class,
                () -> projects.createTask(owner, project.id(), new CreateProjectTaskRequest(
                        "Người ngoài", null, null, null, UUID.randomUUID(), null))).code());
        assertEquals("PROJECT_ACCESS_DENIED", assertThrows(TaskApiException.class,
                () -> projects.deleteTask(first, project.id(), task.id())).code());
    }

    @Test
    void statusFilterPaginationAndSoftDeleteExcludeRemovedTasks() {
        UUID owner = UUID.randomUUID();
        var project = projects.create(owner, "owner@example.com", new CreateProjectRequest("Nhóm", null));
        var todo = projects.createTask(owner, project.id(), new CreateProjectTaskRequest(
                "Chưa làm", null, TaskStatus.TODO, null, null, null));
        projects.createTask(owner, project.id(), new CreateProjectTaskRequest(
                "Đang làm", null, TaskStatus.IN_PROGRESS, null, null, null));
        assertEquals(2, projects.tasks(owner, project.id(), null, 0, 1).totalElements());
        assertEquals(1, projects.tasks(owner, project.id(), null, 0, 1).content().size());
        assertEquals(2, projects.tasks(owner, project.id(), null, 0, 1).totalPages());
        assertEquals(1, projects.tasks(owner, project.id(), TaskStatus.TODO, 0, 20).totalElements());
        assertEquals("INVALID_PAGINATION", assertThrows(TaskApiException.class,
                () -> projects.tasks(owner, project.id(), null, -1, 20)).code());
        assertEquals("INVALID_PAGINATION", assertThrows(TaskApiException.class,
                () -> projects.tasks(owner, project.id(), null, 0, 0)).code());

        projects.deleteTask(owner, project.id(), todo.id());
        assertEquals(1, projects.get(owner, project.id()).taskCount());
        assertEquals(0, projects.tasks(owner, project.id(), TaskStatus.TODO, 0, 20).totalElements());
        assertEquals("PROJECT_TASK_NOT_FOUND", assertThrows(TaskApiException.class,
                () -> projects.task(owner, project.id(), todo.id())).code());
    }

    @Test
    void memberCanLeaveButCannotManageMembershipOrProject() {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();
        var project = projects.create(owner, "owner@example.com", new CreateProjectRequest("Nhóm", null));
        var invitation = projects.invite(owner, project.id(), new InviteMemberRequest("member@example.com"));
        projects.accept(member, "member@example.com", new AcceptInvitationRequest(invitation.id()));

        assertEquals("PROJECT_ACCESS_DENIED", assertThrows(TaskApiException.class,
                () -> projects.invite(member, project.id(), new InviteMemberRequest("new@example.com"))).code());
        assertEquals("PROJECT_ACCESS_DENIED", assertThrows(TaskApiException.class,
                () -> projects.removeMember(member, project.id(), owner)).code());
        assertEquals("PROJECT_ACCESS_DENIED", assertThrows(TaskApiException.class,
                () -> projects.update(member, project.id(),
                        new UpdateProjectRequest("Tên khác", null, "ARCHIVED"))).code());
        assertEquals("OWNER_CANNOT_LEAVE", assertThrows(TaskApiException.class,
                () -> projects.leave(owner, project.id())).code());
        assertEquals("OWNER_CANNOT_LEAVE", assertThrows(TaskApiException.class,
                () -> projects.removeMember(owner, project.id(), owner)).code());
        assertEquals("MEMBER_NOT_FOUND", assertThrows(TaskApiException.class,
                () -> projects.removeMember(owner, project.id(), outsider)).code());

        var task = projects.createTask(owner, project.id(), new CreateProjectTaskRequest(
                "Việc nhóm", null, null, null, member, null));
        projects.leave(member, project.id());
        assertNull(projects.task(owner, project.id(), task.id()).assigneeId());
        assertEquals(1, projects.get(owner, project.id()).memberCount());
        assertEquals("PROJECT_NOT_FOUND", assertThrows(TaskApiException.class,
                () -> projects.get(member, project.id())).code());
        assertEquals("PROJECT_NOT_FOUND", assertThrows(TaskApiException.class,
                () -> projects.task(member, project.id(), task.id())).code());
    }

    @Test
    void pendingInvitationCannotBeAcceptedWhileProjectIsArchived() {
        UUID owner = UUID.randomUUID();
        UUID invitee = UUID.randomUUID();
        var project = projects.create(owner, "owner@example.com", new CreateProjectRequest("Nhóm", null));
        var invitation = projects.invite(owner, project.id(), new InviteMemberRequest("invitee@example.com"));

        projects.update(owner, project.id(), new UpdateProjectRequest("Nhóm", null, "ARCHIVED"));
        assertTrue(projects.invitations("invitee@example.com").isEmpty());
        assertEquals("PROJECT_ARCHIVED", assertThrows(TaskApiException.class,
                () -> projects.accept(invitee, "invitee@example.com",
                        new AcceptInvitationRequest(invitation.id()))).code());
        assertEquals(1, projects.members(owner, project.id()).size());

        projects.update(owner, project.id(), new UpdateProjectRequest("Nhóm", null, "ACTIVE"));
        assertEquals(1, projects.invitations("invitee@example.com").size());
        assertEquals("MEMBER", projects.accept(invitee, "invitee@example.com",
                new AcceptInvitationRequest(invitation.id())).myRole());
    }

    @Test
    void teamTaskStartDateIsPersistedAndMustNotFollowDueDate() {
        UUID owner = UUID.randomUUID();
        var project = projects.create(owner, "owner@example.com", new CreateProjectRequest("Lịch nhóm", null));
        Instant start = Instant.parse("2026-11-01T09:00:00Z");
        Instant due = start.plusSeconds(3600);
        assertEquals("INVALID_TASK_DATES", assertThrows(TaskApiException.class,
                () -> projects.createTask(owner, project.id(), new CreateProjectTaskRequest(
                        "Sai lịch", null, null, null, owner, due, start))).code());
        var created = projects.createTask(owner, project.id(), new CreateProjectTaskRequest(
                "Đúng lịch", null, null, null, owner, start, due));
        assertEquals(start, created.startAt());
        var updated = projects.updateTask(owner, project.id(), created.id(),
                new UpdateProjectTaskRequest(created.title(), null, created.status(),
                        created.priority(), owner, start.plusSeconds(300), due, created.version()));
        assertEquals(start.plusSeconds(300), updated.startAt());
    }
}
