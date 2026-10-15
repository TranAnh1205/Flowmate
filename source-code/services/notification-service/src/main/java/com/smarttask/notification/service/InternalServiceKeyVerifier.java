package com.smarttask.notification.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class InternalServiceKeyVerifier {
    private final byte[] expected;

    public InternalServiceKeyVerifier(@Value("${app.internal-service-key:}") String expected) {
        this.expected = expected == null ? new byte[0] : expected.getBytes(StandardCharsets.UTF_8);
    }

    public void verify(String supplied) {
        byte[] actual = supplied == null ? new byte[0] : supplied.getBytes(StandardCharsets.UTF_8);
        if (expected.length < 32 || !MessageDigest.isEqual(expected, actual)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Thông tin xác thực dịch vụ không hợp lệ");
        }
    }
}
