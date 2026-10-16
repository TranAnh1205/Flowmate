# Chiến lược kiểm thử

## Các tầng kiểm thử

1. Unit test service layer bằng JUnit 5, Mockito: Auth, Task, AI, Notification.
2. Spring context test với H2 cho Auth và Task; kiểm thử tích hợp JDBC cho dự án nhóm. CORS Gateway có unit test cấu hình; preflight HTTP thật nằm trong E2E.
3. TypeScript compiler và Vite production build.
4. E2E qua Gateway với container thật và PostgreSQL thật: `scripts/e2e.mjs`.
5. UI smoke bằng Microsoft Edge: `scripts/team-ui-mock-smoke.mjs` kiểm tra frontend với API giả lập; `scripts/ui-smoke.mjs` cho Admin và `scripts/team-ui-smoke.mjs` cho dự án nhóm trên stack thật.

## Ma trận chính

| Nhóm | Ca được kiểm tra |
|---|---|
| Auth | Register, email trùng, login đúng/sai, JWT, refresh rotation, đổi mật khẩu, thu hồi phiên cũ, logout |
| Ownership | Tài khoản B không xem task của tài khoản A |
| Dự án nhóm | Lời mời đúng email, chấp nhận một lần, cô lập người ngoài, quyền OWNER/MEMBER, giao việc, cập nhật đồng thời và xóa thành viên |
| Task | CRUD danh mục/nhãn/mục tiêu, ngày và phân trang không hợp lệ, checklist CRUD, status, reorder, completion, search, thống kê tuần, outbox reminder và retry/backoff |
| AI | Parse fallback, ngày giờ tiếng Việt, duration/reminder/priority, giới hạn breakdown, daily plan, weekly summary |
| Notification | Ownership, phân trang không hợp lệ, reminder đến hạn tạo notification, unread/read, xác thực service-to-service |
| Admin/RBAC | USER bị chặn 403, tìm kiếm/lọc tài khoản, response không rò password/token, validation phân trang |
| Admin an toàn | Xác thực lại đúng/sai, cấm tự khóa/hạ quyền, cấm cấp quyền cho tài khoản đang khóa, thu hồi phiên, audit log |
| Admin vận hành | Thống kê user/task/notification, AI status đã khử bí mật, health tổng hợp service |
| Trình duyệt | CORS preflight, đăng nhập thật, dashboard 8 thẻ/4 service, tìm user, thu hồi một phiên, USER 403, desktop/mobile, console/network errors |
| Hạ tầng | Flyway trên PostgreSQL, health checks, Swagger, frontend và Gateway HTTP 200 |

## Lệnh chấp nhận cuối

```bash
# Chạy từ gốc repository
mvn clean test
cd frontend && npm run build && cd ..
docker compose config --quiet
docker compose build
docker compose up -d
$env:ADMIN_EMAIL="admin@flowmate.local"
$env:ADMIN_PASSWORD="<mat-khau-admin>"
node scripts/e2e.mjs
$env:CODEX_NODE_MODULES="<duong-dan-node_modules-co-playwright>"
node scripts/ui-smoke.mjs
node scripts/team-ui-smoke.mjs
docker compose ps
```

Nếu không cung cấp hai biến Admin, các ca người dùng cá nhân vẫn chạy và nhóm Admin được bỏ qua có thông báo rõ ràng. Hai script tạo email duy nhất theo timestamp nên có thể chạy lặp lại. UI smoke cần package `playwright` trong thư mục `CODEX_NODE_MODULES` và Microsoft Edge; ảnh cùng báo cáo được lưu tại `artifacts/ui-smoke` ở gốc repository (thư mục bị Git bỏ qua). Database volume được giữ để kiểm tra persistence; dùng `docker compose down -v` chỉ khi muốn reset hoàn toàn.

Sau khi thêm dự án nhóm, `ProjectServiceIntegrationTest` chạy trên H2. Luồng nhóm đã được mở rộng trong `scripts/e2e.mjs` và có UI smoke riêng `scripts/team-ui-smoke.mjs`; cả hai cần chạy lại trên PostgreSQL khi Docker Desktop hoạt động.

Khi chưa có Docker, sau `npm run build` có thể chạy `node scripts/team-ui-mock-smoke.mjs` với `CODEX_NODE_MODULES` trỏ tới thư mục chứa Playwright. Kết quả này chỉ chứng minh frontend và hợp đồng API giả lập, không chứng minh backend/Gateway/PostgreSQL.

Xem thêm [ma trận test case dự án nhóm](team-project-cases.md), gồm các ca về quyền, lời mời, lưu trữ, phân trang và hồi quy sau khi sửa lỗi.
