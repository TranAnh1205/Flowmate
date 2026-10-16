package com.smarttask.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttask.ai.dto.AiDtos.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiAssistantServiceTest {
    @Mock ChatClient.Builder chatBuilder;
    @Mock ChatClient chatClient;
    AiAssistantService service;

    @BeforeEach
    void setUp() {
        when(chatBuilder.build()).thenReturn(chatClient);
        RestClient.Builder offlineClient = RestClient.builder()
                .requestFactory(new SimpleClientHttpRequestFactory());
        service = new AiAssistantService(chatBuilder, new ObjectMapper(), offlineClient, false, "http://localhost:1");
    }

    @Test
    void fallbackParserExtractsPriorityDurationAndReminder() {
        ParsedTaskResponse result = service.parse(new ParseTaskRequest(
                "Hoàn thành tài liệu khẩn trong 2 giờ, nhắc tôi trước 15 phút"));

        assertEquals("URGENT", result.priority());
        assertEquals(120, result.estimatedMinutes());
        assertEquals(15, result.reminderMinutesBefore());
        assertFalse(result.warnings().isEmpty());
    }

    @Test
    void fallbackBreakdownRespectsMaximum() {
        BreakdownResponse result = service.breakdown(new BreakdownRequest("Xây dựng website", "Spring Boot", 3));

        assertEquals(3, result.subtasks().size());
        assertTrue(result.subtasks().stream().allMatch(item -> item.title().contains("Xây dựng website")));
    }

    @Test
    void fallbackParserUnderstandsVietnameseTomorrowEvening() {
        ParsedTaskResponse result = service.parse(new ParseTaskRequest(
                "Tối mai lúc 8 giờ hoàn thiện báo cáo, nhắc tôi trước 30 phút"));

        assertNotNull(result.dueAt());
        assertEquals(LocalDate.now(ZoneId.of("Asia/Bangkok")).plusDays(1), result.dueAt().toLocalDate());
        assertEquals(20, result.dueAt().getHour());
        assertEquals(30, result.reminderMinutesBefore());
    }

    @Test
    void fallbackParserDoesNotTreatClockTimeAsDuration() {
        ParsedTaskResponse result = service.parse(new ParseTaskRequest(
                "Tối mai lúc 8 giờ học Spring Boot trong 2 tiếng, nhắc tôi trước 15 phút"));

        assertEquals(20, result.dueAt().getHour());
        assertEquals(120, result.estimatedMinutes());
        assertEquals(15, result.reminderMinutesBefore());
    }

    @Test
    void fallbackParserRejectsInvalidExplicitDate() {
        ParsedTaskResponse result = service.parse(new ParseTaskRequest(
                "Nộp báo cáo ngày 31/02/2026 lúc 9 giờ"));

        assertNull(result.dueAt());
        assertTrue(result.warnings().stream().anyMatch(value -> value.contains("Không nhận diện")));
    }

    @Test
    void priorityMarksNearDeadlineUrgent() {
        PriorityResponse result = service.priority(new PriorityRequest("Báo cáo", null,
                OffsetDateTime.now().plusHours(2), 60));

        assertEquals("URGENT", result.priority());
    }

    @Test
    void providerResultDoesNotInventTemporalFields() {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-20T10:00:00+07:00");
        ParsedTaskResponse proposed = new ParsedTaskResponse("Xây dựng website", null, "HIGH",
                OffsetDateTime.parse("2024-04-28T09:00:00+07:00"),
                OffsetDateTime.parse("2024-06-30T18:00:00+07:00"),
                4320, null, 1440, List.of(), List.of());

        ParsedTaskResponse result = service.normalizeProviderResult(proposed,
                "Lập kế hoạch xây dựng website, phân tích, thiết kế, phát triển và kiểm thử", now);

        assertNull(result.startAt());
        assertNull(result.dueAt());
        assertNull(result.estimatedMinutes());
        assertNull(result.reminderMinutesBefore());
        assertEquals(4, result.warnings().size());
    }

    @Test
    void providerResultRejectsPastDeadlineEvenWhenRequested() {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-20T10:00:00+07:00");
        ParsedTaskResponse proposed = new ParsedTaskResponse("Bảo vệ đồ án", null, "HIGH", null,
                OffsetDateTime.parse("2024-04-28T20:00:00+07:00"), 120, null, 30,
                List.of(), List.of());

        ParsedTaskResponse result = service.normalizeProviderResult(proposed,
                "Bảo vệ đồ án ngày mai, dự kiến trong 2 giờ, nhắc tôi trước 30 phút", now);

        assertNull(result.dueAt());
        assertEquals(120, result.estimatedMinutes());
        assertNull(result.reminderMinutesBefore());
        assertTrue(result.warnings().stream().anyMatch(item -> item.contains("quá khứ")));
    }

    @Test
    void providerResultAcceptsMissingPriorityAndNullWarnings() {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-20T10:00:00+07:00");
        ParsedTaskResponse proposed = new ParsedTaskResponse("Học Spring Boot", null, null, null,
                OffsetDateTime.parse("2026-08-21T20:00:00+07:00"), 120, null, 15,
                List.of(), java.util.Arrays.asList(null, "", "Kiểm tra lại kế hoạch"));

        ParsedTaskResponse result = service.normalizeProviderResult(proposed,
                "Tối mai lúc 8 giờ học Spring Boot trong 2 tiếng, nhắc tôi trước 15 phút", now);

        assertEquals("MEDIUM", result.priority());
        assertEquals(List.of("Kiểm tra lại kế hoạch"), result.warnings());
        assertEquals(20, result.dueAt().getHour());
        assertEquals("+07:00", result.dueAt().getOffset().toString());
    }

    @Test
    void providerResultCorrectsVietnameseClockTimeDeterministically() {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-20T10:00:00+07:00");
        ParsedTaskResponse proposed = new ParsedTaskResponse("Học Spring Boot", null, "MEDIUM", null,
                OffsetDateTime.parse("2026-08-21T15:00:00Z"), 120, null, null,
                List.of(), List.of());

        ParsedTaskResponse result = service.normalizeProviderResult(proposed,
                "Tối mai lúc 8 giờ học Spring Boot trong 2 tiếng", now);

        assertEquals(OffsetDateTime.parse("2026-08-21T20:00:00+07:00"), result.dueAt());
    }

    @Test
    void providerDecoderAcceptsStringSubtasks() throws Exception {
        ParsedTaskResponse result = service.decodeParsedTask("""
                {"title":"Website","description":null,"priority":"HIGH","startAt":null,
                 "dueAt":null,"estimatedMinutes":null,"categoryName":null,
                 "reminderMinutesBefore":null,"subtasks":["Phân tích","Thiết kế"],"warnings":[]}
                """);

        assertEquals(2, result.subtasks().size());
        assertEquals("Phân tích", result.subtasks().get(0).title());
        assertEquals(0, result.subtasks().get(0).position());
        assertEquals(1, result.subtasks().get(1).position());
    }
}
