package com.smarttask.ai.ratelimit;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AiRateLimitServiceTest {

    @Test
    void rejectsConcurrentAndMinuteLimitRequests() {
        AiRateLimitProperties properties = properties(2, 20, 1);
        AiRateLimitService service = new AiRateLimitService(properties);

        AiRateLimitService.Permit first = service.acquire("user:1", 1);
        AiRateLimitExceededException concurrent = assertThrows(
                AiRateLimitExceededException.class, () -> service.acquire("user:1", 1));
        assertTrue(concurrent.getMessage().contains("đang được xử lý"));

        service.release(first);
        AiRateLimitService.Permit second = service.acquire("user:1", 1);
        service.release(second);

        AiRateLimitExceededException minute = assertThrows(
                AiRateLimitExceededException.class, () -> service.acquire("user:1", 1));
        assertTrue(minute.getMessage().contains("một phút"));
        assertTrue(minute.getRetryAfterSeconds() > 0);
    }

    @Test
    void appliesWeightedDailyQuota() {
        AiRateLimitService service = new AiRateLimitService(properties(100, 2, 1));
        AiRateLimitService.Permit permit = service.acquire("user:2", 2);
        assertEquals(0, permit.dailyRemaining());
        service.release(permit);

        AiRateLimitExceededException daily = assertThrows(
                AiRateLimitExceededException.class, () -> service.acquire("user:2", 1));
        assertTrue(daily.getMessage().contains("trong ngày"));
    }

    private AiRateLimitProperties properties(int perMinute, int perDay, int concurrent) {
        AiRateLimitProperties properties = new AiRateLimitProperties();
        properties.setRequestsPerMinute(perMinute);
        properties.setRequestsPerDay(perDay);
        properties.setMaxConcurrent(concurrent);
        return properties;
    }
}
