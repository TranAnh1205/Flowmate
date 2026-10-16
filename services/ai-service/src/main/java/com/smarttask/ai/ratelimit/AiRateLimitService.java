package com.smarttask.ai.ratelimit;

import java.time.*;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AiRateLimitService {
    private static final long WINDOW_SECONDS = 60;

    private final AiRateLimitProperties properties;
    private final Clock clock;
    private final Map<String, Usage> usageByPrincipal = new ConcurrentHashMap<>();

    @Autowired
    public AiRateLimitService(AiRateLimitProperties properties) {
        this(properties, Clock.systemUTC());
    }

    AiRateLimitService(AiRateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public Permit acquire(String principalKey, int cost) {
        int normalizedCost = Math.max(1, cost);
        if (!properties.isEnabled()) return new Permit(principalKey, Integer.MAX_VALUE, Integer.MAX_VALUE, false);

        Usage usage = usageByPrincipal.computeIfAbsent(principalKey, ignored -> new Usage());
        synchronized (usage) {
            Instant now = clock.instant();
            LocalDate today = LocalDate.now(clock);
            usage.minuteEntries.removeIf(entry -> entry.at().isBefore(now.minusSeconds(WINDOW_SECONDS)));
            if (!today.equals(usage.day)) {
                usage.day = today;
                usage.dailyUsed = 0;
            }

            if (usage.active >= positive(properties.getMaxConcurrent())) {
                throw new AiRateLimitExceededException(
                        "Một yêu cầu AI khác đang được xử lý. Vui lòng đợi yêu cầu hiện tại hoàn tất.", 2);
            }

            int minuteUsed = usage.minuteEntries.stream().mapToInt(Entry::cost).sum();
            if (minuteUsed + normalizedCost > positive(properties.getRequestsPerMinute())) {
                Entry oldest = usage.minuteEntries.peekFirst();
                long retryAfter = oldest == null ? WINDOW_SECONDS
                        : Math.max(1, WINDOW_SECONDS - Duration.between(oldest.at(), now).getSeconds());
                throw new AiRateLimitExceededException(
                        "Bạn đã đạt giới hạn yêu cầu AI trong một phút.", retryAfter);
            }

            if (usage.dailyUsed + normalizedCost > positive(properties.getRequestsPerDay())) {
                long retryAfter = Duration.between(now,
                        today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()).getSeconds();
                throw new AiRateLimitExceededException(
                        "Bạn đã sử dụng hết hạn mức AI trong ngày.", retryAfter);
            }

            usage.minuteEntries.addLast(new Entry(now, normalizedCost));
            usage.dailyUsed += normalizedCost;
            usage.active++;
            return new Permit(principalKey,
                    Math.max(0, properties.getRequestsPerMinute() - minuteUsed - normalizedCost),
                    Math.max(0, properties.getRequestsPerDay() - usage.dailyUsed), true);
        }
    }

    public void release(Permit permit) {
        if (permit == null || !permit.counted()) return;
        Usage usage = usageByPrincipal.get(permit.principalKey());
        if (usage == null) return;
        synchronized (usage) {
            usage.active = Math.max(0, usage.active - 1);
        }
    }

    public int minuteLimit() { return positive(properties.getRequestsPerMinute()); }
    public int dailyLimit() { return positive(properties.getRequestsPerDay()); }

    private int positive(int value) { return Math.max(1, value); }

    public record Permit(String principalKey, int minuteRemaining, int dailyRemaining, boolean counted) {}
    private record Entry(Instant at, int cost) {}

    private static final class Usage {
        private final Deque<Entry> minuteEntries = new ArrayDeque<>();
        private LocalDate day;
        private int dailyUsed;
        private int active;
    }
}
