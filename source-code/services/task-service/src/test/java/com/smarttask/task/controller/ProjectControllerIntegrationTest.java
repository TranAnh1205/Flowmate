package com.smarttask.task.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Map;
import java.util.Locale;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.http.client.factory=simple",
        "app.reminder-sync.poll-delay-ms=60000"
})
@AutoConfigureMockMvc
@Sql(scripts = {"classpath:db/migration/V3__add_team_projects.sql",
                "classpath:db/migration/V7__add_team_task_start_date.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class ProjectControllerIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void projectInvitationAndTaskFlowEnforcesHttpAuthorization() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();

        mvc.perform(get("/api/v1/projects"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/projects")
                        .with(account(owner, "owner@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        JsonNode project = body(mvc.perform(post("/api/v1/projects")
                        .with(account(owner, "OWNER@Example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Nhóm kiểm thử"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.myRole").value("OWNER"))
                .andReturn().getResponse().getContentAsString());
        String projectId = project.get("id").asText();

        mvc.perform(get("/api/v1/projects/{id}", projectId)
                        .with(account(outsider, "outsider@example.com")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));

        JsonNode invitation = body(mvc.perform(post("/api/v1/projects/{id}/invitations", projectId)
                        .with(account(owner, "owner@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", "MEMBER@example.com"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invitedEmail").value("member@example.com"))
                .andReturn().getResponse().getContentAsString());
        String invitationId = invitation.get("id").asText();

        mvc.perform(get("/api/v1/projects/invitations")
                        .with(account(outsider, "outsider@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(post("/api/v1/projects/invitations/accept")
                        .with(account(outsider, "outsider@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("invitationId", invitationId))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVITATION_NOT_FOUND"));
        mvc.perform(post("/api/v1/projects/invitations/accept")
                        .with(account(member, "member@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("invitationId", invitationId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").value("MEMBER"));

        JsonNode task = body(mvc.perform(post("/api/v1/projects/{id}/tasks", projectId)
                        .with(account(owner, "owner@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "title", "Việc của thành viên", "assigneeId", member))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assigneeId").value(member.toString()))
                .andReturn().getResponse().getContentAsString());
        String taskId = task.get("id").asText();
        long version = task.get("version").asLong();

        mvc.perform(patch("/api/v1/projects/{id}/tasks/{taskId}/status", projectId, taskId)
                        .with(account(outsider, "outsider@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("status", "COMPLETED", "version", version))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
        mvc.perform(patch("/api/v1/projects/{id}/tasks/{taskId}/status", projectId, taskId)
                        .with(account(member, "member@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("status", "COMPLETED", "version", version))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mvc.perform(get("/api/v1/projects/{id}", projectId)
                        .with(account(owner, "owner@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedTaskCount").value(1));
        mvc.perform(get("/api/v1/projects/{id}/tasks", projectId)
                        .with(account(member, "member@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Việc của thành viên"));
    }

    @Test
    void invitationEmailClaimUsesLocaleIndependentNormalization() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID invitee = UUID.randomUUID();
        JsonNode project = body(mvc.perform(post("/api/v1/projects")
                        .with(account(owner, "owner@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Nhóm locale"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        String projectId = project.get("id").asText();
        JsonNode invitation = body(mvc.perform(post("/api/v1/projects/{id}/invitations", projectId)
                        .with(account(owner, "owner@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", "INVITE@example.com"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            mvc.perform(get("/api/v1/projects/invitations")
                            .with(account(invitee, "INVITE@example.com")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
            mvc.perform(post("/api/v1/projects/invitations/accept")
                            .with(account(invitee, "INVITE@example.com"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of(
                                    "invitationId", invitation.get("id").asText()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.myRole").value("MEMBER"));
        } finally {
            Locale.setDefault(previous);
        }
    }

    private RequestPostProcessor account(UUID userId, String email) {
        return jwt().jwt(token -> token.subject(userId.toString()).claim("email", email));
    }

    private JsonNode body(String response) throws Exception {
        return json.readTree(response);
    }
}
