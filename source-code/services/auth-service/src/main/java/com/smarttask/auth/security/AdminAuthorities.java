package com.smarttask.auth.security;

import java.util.List;

public final class AdminAuthorities {
    public static final String USER_VIEW = "USER_VIEW";
    public static final String USER_SUSPEND = "USER_SUSPEND";
    public static final String USER_ROLE_UPDATE = "USER_ROLE_UPDATE";
    public static final String SESSION_REVOKE = "SESSION_REVOKE";
    public static final String AUDIT_VIEW = "AUDIT_VIEW";
    public static final String SYSTEM_HEALTH_VIEW = "SYSTEM_HEALTH_VIEW";
    public static final String AI_MONITOR_VIEW = "AI_MONITOR_VIEW";

    public static final List<String> ALL = List.of(
            USER_VIEW,
            USER_SUSPEND,
            USER_ROLE_UPDATE,
            SESSION_REVOKE,
            AUDIT_VIEW,
            SYSTEM_HEALTH_VIEW,
            AI_MONITOR_VIEW
    );

    private AdminAuthorities() {}
}
