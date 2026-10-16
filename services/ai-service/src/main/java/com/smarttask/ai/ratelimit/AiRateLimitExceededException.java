package com.smarttask.ai.ratelimit;

public class AiRateLimitExceededException extends RuntimeException {
    private final long retryAfterSeconds;

    public AiRateLimitExceededException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() { return retryAfterSeconds; }
}
