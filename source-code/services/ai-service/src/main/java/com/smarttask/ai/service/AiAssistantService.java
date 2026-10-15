package com.smarttask.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smarttask.ai.dto.AiDtos.BreakdownRequest;
import com.smarttask.ai.dto.AiDtos.BreakdownResponse;
import com.smarttask.ai.dto.AiDtos.DailyPlanRequest;
import com.smarttask.ai.dto.AiDtos.DailyPlanResponse;
import com.smarttask.ai.dto.AiDtos.ParseTaskRequest;
import com.smarttask.ai.dto.AiDtos.ParsedTaskResponse;
import com.smarttask.ai.dto.AiDtos.PriorityRequest;
import com.smarttask.ai.dto.AiDtos.PriorityResponse;
import com.smarttask.ai.dto.AiDtos.ScheduleItem;
import com.smarttask.ai.dto.AiDtos.SubtaskSuggestion;
import com.smarttask.ai.dto.AiDtos.WeeklySummaryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiAssistantService {
    private static final Logger log = LoggerFactory.getLogger(AiAssistantService.class);
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Bangkok");
    private static final Set<String> PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH", "URGENT");
    private static final Pattern DURATION_HOURS = Pattern.compile(
            "(?:trong|mất|dự kiến|khoảng|ước tính)\\s+(\\d+)\\s*(?:tiếng|giờ)|(\\d+)\\s*tiếng",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern DURATION_MINUTES = Pattern.compile(
            "(?:trong|mất|dự kiến|khoảng|ước tính)\\s+(\\d+)\\s*phút",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern REMINDER = Pattern.compile("nhắc(?: tôi)? trước\\s*(\\d+)\\s*phút", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXPLICIT_DATE = Pattern.compile("ngày\\s+(\\d{1,2})/(\\d{1,2})(?:/(\\d{4}))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern CLOCK_TIME = Pattern.compile("(?:lúc|vào)\\s*(\\d{1,2})(?::(\\d{2})|\\s*giờ(?:\\s*(\\d{1,2}))?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DUE_HINT = Pattern.compile(
            "deadline|hạn|hôm nay|ngày mai|tối mai|\\bmai\\b|ngày\\s+\\d{1,2}[/-]\\d{1,2}|"
                    + "thứ\\s+(?:hai|ba|tư|năm|sáu|bảy)|chủ nhật|tuần (?:này|sau)|tháng (?:này|sau)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern START_HINT = Pattern.compile(
            "bắt đầu|khởi động|thực hiện từ|từ ngày|từ lúc",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern DURATION_HINT = Pattern.compile(
            "(?:trong|mất|dự kiến|khoảng|ước tính)[^,.\\n]{0,24}\\d+\\s*(?:phút|giờ|tiếng|ngày)|"
                    + "\\d+\\s*tiếng",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final ChatClient chat;
    private final ObjectMapper mapper;
    private final RestClient taskClient;
    private final boolean enabled;

    public AiAssistantService(ChatClient.Builder builder,
                              ObjectMapper mapper,
                              RestClient.Builder rest,
                              @Value("${app.ai.enabled:false}") boolean enabled,
                              @Value("${app.services.task-url:http://localhost:8082}") String taskUrl) {
        this.chat = builder.build();
        this.mapper = mapper;
        this.taskClient = rest.baseUrl(taskUrl).build();
        this.enabled = enabled;
    }

    public ParsedTaskResponse parse(ParseTaskRequest request) {
        if (enabled) {
            try {
                OffsetDateTime now = OffsetDateTime.now(DEFAULT_ZONE);
                String json = chat.prompt()
                        .system("Bạn là trợ lý quản lý công việc cá nhân. Thời điểm hiện tại là " + now + ". "
                                + "Trả về duy nhất một JSON object với các trường title, description, priority, "
                                + "startAt, dueAt, estimatedMinutes, categoryName, reminderMinutesBefore, subtasks, warnings. "
                                + "Priority chỉ LOW, MEDIUM, HIGH hoặc URGENT. startAt và dueAt dùng ISO-8601 với múi giờ +07:00. "
                                + "Không được tự tạo startAt/dueAt nếu người dùng không nêu ngày hoặc thời điểm tương ứng; "
                                + "không được trả ngày trong quá khứ. Không tự tạo estimatedMinutes nếu người dùng không nêu "
                                + "thời lượng; đây là số phút làm việc thực tế, không phải số phút giữa hai ngày. "
                                + "Không tự tạo reminderMinutesBefore nếu người dùng không yêu cầu nhắc. "
                                + "subtasks phải là mảng object dạng "
                                + "{\"title\":\"...\",\"estimatedMinutes\":60,\"position\":0}; position bắt đầu từ 0. "
                                + "Trường không xác định phải là null hoặc mảng rỗng, tuyệt đối không đoán.")
                        .user(request.prompt())
                        .call()
                        .content();
                ParsedTaskResponse value = normalizeProviderResult(
                        decodeParsedTask(json), request.prompt(), now);
                if (value.title() != null && !value.title().isBlank()) return value;
            } catch (Exception error) {
                log.warn("AI provider failed; using local parser: {}", error.getClass().getSimpleName());
            }
        }
        return fallbackParse(request.prompt());
    }

    public BreakdownResponse breakdown(BreakdownRequest request) {
        int maximum = request.maximumSubtasks() == null ? 6 : request.maximumSubtasks();
        if (enabled) {
            try {
                String json = chat.prompt()
                        .system("Chia công việc thành tối đa " + maximum
                                + " bước. Trả về JSON dạng {\"subtasks\":[{\"title\":\"...\","
                                + "\"estimatedMinutes\":60,\"position\":1}]}.")
                        .user(request.title() + "\n" + Objects.toString(request.description(), ""))
                        .call()
                        .content();
                BreakdownResponse value = mapper.readValue(clean(json), BreakdownResponse.class);
                if (value.subtasks() != null && !value.subtasks().isEmpty()) {
                    return new BreakdownResponse(value.subtasks().stream()
                            .filter(item -> item.title() != null && !item.title().isBlank())
                            .limit(maximum)
                            .toList());
                }
            } catch (Exception error) {
                log.warn("AI breakdown failed; using local breakdown: {}", error.getClass().getSimpleName());
            }
        }
        List<String> steps = List.of(
                "Xác định kết quả cần đạt",
                "Thu thập thông tin và tài liệu cần thiết",
                "Lập kế hoạch thực hiện",
                "Thực hiện phần việc chính",
                "Kiểm tra và điều chỉnh",
                "Hoàn thiện và tổng kết");
        List<SubtaskSuggestion> output = new ArrayList<>();
        for (int index = 0; index < Math.min(maximum, steps.size()); index++) {
            output.add(new SubtaskSuggestion(steps.get(index) + ": " + request.title(), 60, index));
        }
        return new BreakdownResponse(output);
    }

    public PriorityResponse priority(PriorityRequest request) {
        if (request.dueAt() != null && request.dueAt().isBefore(OffsetDateTime.now().plusDays(1))) {
            return new PriorityResponse("URGENT", "Deadline còn dưới 24 giờ");
        }
        if (request.dueAt() != null && request.dueAt().isBefore(OffsetDateTime.now().plusDays(3))) {
            return new PriorityResponse("HIGH", "Deadline đang đến gần");
        }
        if (request.estimatedMinutes() != null && request.estimatedMinutes() > 240) {
            return new PriorityResponse("HIGH", "Công việc cần nhiều thời gian để hoàn thành");
        }
        return new PriorityResponse("MEDIUM", "Chưa có yếu tố khẩn cấp rõ ràng");
    }

    @SuppressWarnings("unchecked")
    public DailyPlanResponse dailyPlan(DailyPlanRequest request, String authorization) {
        try {
            Map<String, Object> page = taskClient.get()
                    .uri("/api/v1/tasks?status=TODO&page=0&size=100&sort=dueAt,asc")
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .retrieve()
                    .body(Map.class);
            List<Map<String, Object>> content = page == null
                    ? List.of()
                    : (List<Map<String, Object>>) page.getOrDefault("content", List.of());
            LocalTime cursor = LocalTime.of(8, 0);
            List<ScheduleItem> schedule = new ArrayList<>();
            List<UUID> unscheduled = new ArrayList<>();
            for (Map<String, Object> task : content) {
                UUID id = UUID.fromString(task.get("id").toString());
                int minutes = task.get("estimatedMinutes") instanceof Number number ? number.intValue() : 60;
                if (cursor.plusMinutes(minutes).isAfter(LocalTime.of(17, 30))) {
                    unscheduled.add(id);
                    continue;
                }
                schedule.add(new ScheduleItem(id, task.get("title").toString(), cursor,
                        cursor.plusMinutes(minutes), "Ưu tiên theo deadline và thứ tự hiện tại"));
                cursor = cursor.plusMinutes(minutes + 15L);
            }
            List<String> warnings = unscheduled.isEmpty()
                    ? List.of()
                    : List.of("Một số công việc chưa thể xếp trong giờ làm việc");
            return new DailyPlanResponse(request.date(), schedule, unscheduled, warnings);
        } catch (Exception error) {
            log.warn("Daily plan could not load tasks: {}", error.getClass().getSimpleName());
            return new DailyPlanResponse(request.date(), List.of(), List.of(),
                    List.of("Không thể lấy danh sách công việc để lập kế hoạch"));
        }
    }

    @SuppressWarnings("unchecked")
    public WeeklySummaryResponse weekly(String authorization) {
        try {
            Map<String, Object> statistics = taskClient.get()
                    .uri("/api/v1/statistics/dashboard")
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .retrieve()
                    .body(Map.class);
            if (statistics == null) throw new IllegalStateException("Empty statistics response");
            long total = number(statistics, "totalTasks");
            long completed = number(statistics, "completed");
            long overdue = number(statistics, "overdue");
            double rate = total == 0 ? 0 : Math.round(completed * 1000.0 / total) / 10.0;
            String summary = "Bạn đã hoàn thành " + completed + " trong tổng số " + total + " công việc.";
            List<String> strengths = completed > 0
                    ? List.of("Bạn đang duy trì tiến độ hoàn thành công việc") : List.of();
            List<String> suggestions = overdue > 0
                    ? List.of("Ưu tiên xử lý " + overdue + " công việc quá hạn",
                    "Chia nhỏ công việc lớn thành checklist")
                    : List.of("Tiếp tục duy trì nhịp làm việc hiện tại");
            return new WeeklySummaryResponse(summary, total, completed, overdue, rate, strengths, suggestions);
        } catch (Exception error) {
            log.warn("Weekly summary could not load statistics: {}", error.getClass().getSimpleName());
            return new WeeklySummaryResponse("Chưa thể tạo tóm tắt lúc này", 0, 0, 0, 0,
                    List.of(), List.of("Hãy thử lại sau"));
        }
    }

    private long number(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value instanceof Number number ? number.longValue() : 0;
    }

    ParsedTaskResponse normalizeProviderResult(ParsedTaskResponse value, String prompt, OffsetDateTime now) {
        String requestedPriority = value.priority() == null
                ? null
                : value.priority().trim().toUpperCase();
        String priority = requestedPriority != null && PRIORITIES.contains(requestedPriority)
                ? requestedPriority
                : "MEDIUM";
        Integer duration = positiveOrNull(value.estimatedMinutes());
        Integer reminder = nonNegativeOrNull(value.reminderMinutesBefore());
        OffsetDateTime start = value.startAt();
        OffsetDateTime due = value.dueAt();
        List<String> warnings = value.warnings() == null
                ? new ArrayList<>()
                : new ArrayList<>(value.warnings().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList());

        if (!START_HINT.matcher(prompt).find()) {
            if (start != null) warnings.add("Đã bỏ thời gian bắt đầu do yêu cầu chưa nêu rõ");
            start = null;
        }
        if (!DUE_HINT.matcher(prompt).find()) {
            if (due != null) warnings.add("Đã bỏ deadline do yêu cầu chưa nêu rõ");
            due = null;
        } else {
            OffsetDateTime deterministicDue = parseDueAt(
                    prompt, prompt.toLowerCase(Locale.ROOT), now.toLocalDate());
            if (deterministicDue != null) due = deterministicDue;
        }
        if (!DURATION_HINT.matcher(prompt).find()) {
            if (duration != null) warnings.add("Đã bỏ thời gian dự kiến do yêu cầu chưa nêu thời lượng");
            duration = null;
        }
        if (!REMINDER.matcher(prompt).find()) {
            if (reminder != null) warnings.add("Đã bỏ thời gian nhắc do yêu cầu chưa yêu cầu nhắc việc");
            reminder = null;
        }
        OffsetDateTime pastBoundary = now.minusMinutes(5);
        if (start != null && start.isBefore(pastBoundary)) {
            start = null;
            warnings.add("Thời gian bắt đầu do AI đề xuất đã ở quá khứ và được bỏ qua");
        }
        if (due != null && due.isBefore(pastBoundary)) {
            due = null;
            warnings.add("Deadline do AI đề xuất đã ở quá khứ và được bỏ qua");
        }
        if (start != null && due != null && due.isBefore(start)) {
            due = null;
            warnings.add("Thời hạn do AI đề xuất không hợp lệ và đã được bỏ qua");
        }
        if (due == null && reminder != null) {
            reminder = null;
            warnings.add("Thời gian nhắc được bỏ qua vì công việc chưa có deadline");
        }
        return new ParsedTaskResponse(value.title(), value.description(), priority, start, due, duration,
                value.categoryName(), reminder,
                value.subtasks() == null ? List.of() : value.subtasks(), List.copyOf(warnings));
    }

    ParsedTaskResponse decodeParsedTask(String json) throws Exception {
        JsonNode root = mapper.readTree(clean(json));
        JsonNode subtasks = root.get("subtasks");
        if (root instanceof ObjectNode object && subtasks instanceof ArrayNode array) {
            ArrayNode normalized = mapper.createArrayNode();
            int position = 0;
            for (JsonNode item : array) {
                if (item.isTextual()) {
                    ObjectNode converted = mapper.createObjectNode();
                    converted.put("title", item.asText());
                    converted.putNull("estimatedMinutes");
                    converted.put("position", position);
                    normalized.add(converted);
                } else if (item.isObject()) {
                    ObjectNode converted = item.deepCopy();
                    if (!converted.hasNonNull("position")) converted.put("position", position);
                    normalized.add(converted);
                }
                position++;
            }
            object.set("subtasks", normalized);
        }
        return mapper.treeToValue(root, ParsedTaskResponse.class);
    }

    private ParsedTaskResponse fallbackParse(String prompt) {
        String value = prompt.trim();
        String lower = value.toLowerCase();
        String priority = lower.contains("khẩn") ? "URGENT"
                : lower.contains("ưu tiên cao") ? "HIGH" : "MEDIUM";

        Integer minutes = null;
        Matcher durationHours = DURATION_HOURS.matcher(value);
        if (durationHours.find()) {
            String amount = durationHours.group(1) != null
                    ? durationHours.group(1)
                    : durationHours.group(2);
            minutes = Integer.parseInt(amount) * 60;
        } else {
            Matcher durationMinutes = DURATION_MINUTES.matcher(value);
            if (durationMinutes.find()) minutes = Integer.parseInt(durationMinutes.group(1));
        }

        Matcher reminderMatcher = REMINDER.matcher(value);
        Integer reminder = reminderMatcher.find() ? Integer.parseInt(reminderMatcher.group(1)) : null;
        OffsetDateTime dueAt = parseDueAt(value, lower, LocalDate.now(DEFAULT_ZONE));
        List<String> warnings = new ArrayList<>();
        warnings.add("Kết quả do bộ phân tích cục bộ tạo; hãy kiểm tra trước khi xác nhận");
        if ((lower.contains("mai") || lower.contains("ngày ")) && dueAt == null) {
            warnings.add("Không nhận diện được đầy đủ ngày giờ trong yêu cầu");
        }
        return new ParsedTaskResponse(value, null, priority, null, dueAt, minutes, null, reminder,
                List.of(), List.copyOf(warnings));
    }

    private OffsetDateTime parseDueAt(String prompt, String lower, LocalDate referenceDate) {
        LocalDate date = null;
        if (lower.contains("ngày mai") || lower.contains("tối mai") || lower.matches(".*\\bmai\\b.*")) {
            date = referenceDate.plusDays(1);
        } else if (lower.contains("hôm nay")) {
            date = referenceDate;
        }

        Matcher explicitDate = EXPLICIT_DATE.matcher(prompt);
        if (explicitDate.find()) {
            int year = explicitDate.group(3) == null
                    ? referenceDate.getYear()
                    : Integer.parseInt(explicitDate.group(3));
            try {
                date = LocalDate.of(year, Integer.parseInt(explicitDate.group(2)),
                        Integer.parseInt(explicitDate.group(1)));
            } catch (DateTimeException ignored) {
                return null;
            }
        }

        Matcher timeMatcher = CLOCK_TIME.matcher(prompt);
        if (date == null || !timeMatcher.find()) return null;
        int hour = Integer.parseInt(timeMatcher.group(1));
        int minute = timeMatcher.group(2) != null ? Integer.parseInt(timeMatcher.group(2))
                : timeMatcher.group(3) != null ? Integer.parseInt(timeMatcher.group(3)) : 0;
        if ((lower.contains("tối") || lower.contains("chiều")) && hour < 12) hour += 12;
        if (hour > 23 || minute > 59) return null;
        ZoneOffset offset = DEFAULT_ZONE.getRules().getOffset(date.atTime(hour, minute));
        return OffsetDateTime.of(date, LocalTime.of(hour, minute), offset);
    }

    private Integer positiveOrNull(Integer value) {
        return value != null && value > 0 ? value : null;
    }

    private Integer nonNegativeOrNull(Integer value) {
        return value != null && value >= 0 ? value : null;
    }

    private String clean(String value) {
        String result = value.trim();
        if (result.startsWith("```")) {
            result = result.replaceFirst("^```(?:json)?\\s*", "");
            result = result.replaceFirst("\\s*```$", "");
        }
        return result;
    }
}
