# Kế hoạch thực hiện trong 5 tuần

## Tuần 1 — Nền tảng và xác thực

- Chốt phạm vi cá nhân, user stories và kiến trúc.
- Monorepo, parent Maven, React/Vite, Docker Compose, PostgreSQL.
- Gateway, CORS, health check.
- Auth Service, BCrypt, RS256 JWT/JWKS, refresh token, profile/settings.
- Kết quả demo: đăng ký/đăng nhập và route bảo vệ hoạt động.

## Tuần 2 — Quản lý công việc cốt lõi

- Task/Category/Tag/Goal/Subtask schema và Flyway.
- CRUD, filter/search, Today/Upcoming/Overdue, soft delete.
- Ownership theo JWT, validation và error format.
- Dashboard statistics và unit tests.
- Kết quả demo: quản lý đầy đủ task cá nhân bằng API.

## Tuần 3 — Giao diện và trải nghiệm

- Authentication UI, layout responsive, dashboard.
- Task list/form, phân loại, mục tiêu, lịch.
- Kanban dnd-kit có optimistic update/rollback.
- Axios refresh interceptor và TanStack Query cache.
- Kết quả demo: luồng người dùng chính hoàn chỉnh trên desktop/mobile.

## Tuần 4 — AI và nhắc việc

- AI Service: parse, breakdown, priority, daily plan, weekly summary.
- Fallback cục bộ khi chưa có API key; không để lỗi AI làm crash ứng dụng.
- Preview/edit/select trước khi lưu task.
- Notification Service, reminder scheduler, đồng bộ Task → Reminder.
- Kết quả demo: câu tiếng Việt → task có checklist/reminder.

## Tuần 5 — Hoàn thiện và bàn giao

- Unit/context/E2E tests, kiểm tra ownership và refresh rotation.
- Docker health/restart/dependency, persistence key và database volume.
- Swagger, README, kiến trúc, database, API và hướng dẫn test.
- Kiểm tra production frontend bundle, log lỗi, responsive và demo script.
- Tiêu chí Done: build sạch, container healthy, E2E pass, không secret, không lỗi compile.
