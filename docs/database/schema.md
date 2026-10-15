# Database và migration

Mỗi service sở hữu database riêng; không có foreign key xuyên database. Hibernate dùng `ddl-auto=validate`, còn Flyway là nguồn sự thật của schema.

## auth_db

```mermaid
erDiagram
    USERS ||--|| USER_SETTINGS : has
    USERS ||--o{ REFRESH_TOKENS : owns
    USERS ||--o{ ADMIN_ACTION_TOKENS : owns
    USERS ||--o{ ADMIN_AUDIT_LOGS : performs
    USERS {
      uuid id PK
      varchar email UK
      varchar password_hash
      varchar timezone
      varchar role
      varchar status
      varchar status_reason
      timestamp status_changed_at
      uuid status_changed_by
      timestamp last_login_at
    }
    USER_SETTINGS {
      uuid id PK
      uuid user_id UK
      time work_start_time
      time work_end_time
      int default_reminder_minutes
    }
    REFRESH_TOKENS {
      uuid id PK
      uuid user_id
      varchar token_hash UK
      timestamp expires_at
      timestamp revoked_at
      varchar revoked_reason
      varchar ip_address
      varchar user_agent
      timestamp last_used_at
    }
    ADMIN_ACTION_TOKENS {
      uuid id PK
      uuid admin_user_id
      varchar token_hash UK
      timestamp expires_at
      timestamp used_at
    }
    ADMIN_AUDIT_LOGS {
      uuid id PK
      uuid actor_user_id
      uuid target_user_id
      varchar action
      varchar reason
      varchar ip_address
      varchar user_agent
      timestamp created_at
    }
```

`users.role` chỉ nhận `USER/ADMIN`; `users.status` nhận `ACTIVE/LOCKED/SUSPENDED/DEACTIVATED`. Giao diện quản trị hiện cho phép chuyển `ACTIVE/SUSPENDED`; các trạng thái còn lại được dành cho chính sách khóa tự động hoặc đóng tài khoản sau này.

## task_db

```mermaid
erDiagram
    TASKS ||--o{ SUBTASKS : contains
    TASKS ||--o{ TASK_TAGS : tagged
    TAGS ||--o{ TASK_TAGS : maps
    CATEGORIES ||--o{ TASKS : classifies
    GOALS ||--o{ TASKS : groups
```

Task dùng `Instant` cho thời điểm, UUID cho ID, enum dạng chuỗi cho status/priority, `@Version` cho optimistic locking và `deleted_at` cho xóa mềm.
`tasks.reminder_minutes_before` lưu cấu hình nhắc của task. `reminder_sync_commands` là transactional outbox theo `task_id`; command UPSERT/DELETE được retry cho đến khi Notification Service xác nhận thành công.

Nhóm dự án sử dụng `team_projects`, `team_project_members`, `team_project_invitations` và `team_project_tasks` trong cùng `task_db`. Lời mời gắn với email tài khoản và hết hạn sau 7 ngày; assignee của task nhóm phải là thành viên dự án. Task nhóm có `version` để phát hiện cập nhật đồng thời và `deleted_at` để xóa mềm. Bốn bảng này không thay đổi bảng `tasks` cá nhân.

## notification_db

`reminders` lưu lịch và trạng thái PENDING/PROCESSING/PROCESSED. `notifications` lưu thông báo trong ứng dụng và thời điểm đọc. Cặp task/user được Notification Service quản lý qua REST, không có FK tới task_db.

Migration hiện tại:

- `services/auth-service/src/main/resources/db/migration/V1__create_auth_tables.sql`
- `services/auth-service/src/main/resources/db/migration/V2__add_admin_security.sql`
- `services/task-service/src/main/resources/db/migration/V1__create_task_tables.sql`
- `services/task-service/src/main/resources/db/migration/V2__add_reminder_sync_outbox.sql`
- `services/task-service/src/main/resources/db/migration/V3__add_team_projects.sql`
- `services/notification-service/src/main/resources/db/migration/V1__create_notification_tables.sql`
