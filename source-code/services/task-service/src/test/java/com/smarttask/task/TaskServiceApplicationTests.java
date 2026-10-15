package com.smarttask.task;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Context loading does not need a live HTTP client or a Windows loopback socket.
@SpringBootTest(properties = "spring.http.client.factory=simple")
class TaskServiceApplicationTests {
    @Test
    void contextLoads() {}
}

