package com.smarttask.notification.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InternalServiceKeyVerifierTest {
    private static final String KEY = "0123456789abcdef0123456789abcdef";

    @Test
    void acceptsMatchingStrongKey() {
        InternalServiceKeyVerifier verifier = new InternalServiceKeyVerifier(KEY);
        assertDoesNotThrow(() -> verifier.verify(KEY));
    }

    @Test
    void rejectsMissingWrongOrShortConfiguredKey() {
        assertThrows(ResponseStatusException.class,
                () -> new InternalServiceKeyVerifier(KEY).verify("wrong"));
        assertThrows(ResponseStatusException.class,
                () -> new InternalServiceKeyVerifier("short").verify("short"));
        assertThrows(ResponseStatusException.class,
                () -> new InternalServiceKeyVerifier("").verify(null));
    }
}
