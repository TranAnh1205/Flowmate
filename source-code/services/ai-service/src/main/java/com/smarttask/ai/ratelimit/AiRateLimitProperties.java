package com.smarttask.ai.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.ai.rate-limit")
public class AiRateLimitProperties {
    private boolean enabled = true;
    private int requestsPerMinute = 10;
    private int requestsPerDay = 30;
    private int maxConcurrent = 1;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getRequestsPerMinute() { return requestsPerMinute; }
    public void setRequestsPerMinute(int requestsPerMinute) { this.requestsPerMinute = requestsPerMinute; }
    public int getRequestsPerDay() { return requestsPerDay; }
    public void setRequestsPerDay(int requestsPerDay) { this.requestsPerDay = requestsPerDay; }
    public int getMaxConcurrent() { return maxConcurrent; }
    public void setMaxConcurrent(int maxConcurrent) { this.maxConcurrent = maxConcurrent; }
}
