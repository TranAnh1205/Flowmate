# Flowmate — Personal and Team Task Management

Flowmate là website quản lý công việc **cá nhân và dự án nhóm** tích hợp AI, xây dựng theo kiến trúc microservice với Java 21, Spring Boot, React và PostgreSQL. Dự án nhóm nằm trong Task Service; chưa tách Project Service riêng.

## Chức năng đã có

- Đăng ký, đăng nhập, access token JWT RS256, refresh token xoay vòng, đăng xuất và đổi mật khẩu.
- Hồ sơ, múi giờ, giờ làm việc, giao diện và thời gian nhắc mặc định.
- CRUD công việc; tìm kiếm, lọc, Today, Upcoming, Overdue và xóa mềm.
- Kanban kéo thả với optimistic update và rollback khi API lỗi.
- Checklist, danh mục, nhãn, mục tiêu cá nhân, lịch tháng và dashboard dữ liệu thật.
- Dự án nhóm, lời mời trong ứng dụng theo email tài khoản, thành viên OWNER/MEMBER, giao việc và tiến độ.
- Dự án nhóm có nhiều board/list/card: kéo thả, thứ tự list/card, sửa, sao chép, chuyển board, lưu trữ/khôi phục card. Task nhóm cũ được giữ và hiển thị trên board mặc định.
- Chi tiết card có checklist, nhãn, người theo dõi, bình luận, activity và tệp đính kèm tối đa 5 MB; chat theo board được lưu trong PostgreSQL.
- Board có các chế độ Kanban, bảng dữ liệu, lịch hạn, thống kê cơ bản và tìm thẻ. Giao diện cập nhật bằng polling 3 giây khi đang mở board.
- Ba mẫu board (cơ bản, sprint, nội dung) và danh sách board đã xem gần đây đã hoạt động trên Docker local; Flyway V6 và E2E API đã đạt.
- Reminder đồng bộ theo vòng đời công việc và notification trong ứng dụng.
- AI phân tích câu lệnh tiếng Việt, gợi ý ưu tiên, chia nhỏ công việc, lập kế hoạch ngày và tóm tắt tuần.
- AI chỉ tạo bản xem trước; người dùng sửa/chọn bước và xác nhận trước khi Task Service ghi database.
- Khu vực quản trị riêng: dashboard tổng quan, tìm kiếm/lọc tài khoản, khóa/mở khóa, cấp/thu hồi quyền Admin, quản lý phiên đăng nhập và nhật ký kiểm toán.
- Thao tác quản trị nhạy cảm bắt buộc xác thực lại mật khẩu; action token chỉ có hiệu lực 5 phút, không cho tự khóa/hạ quyền và không cho vô hiệu hóa Admin cuối cùng.
- Màn hình vận hành hiển thị trạng thái Auth/Task/AI/Notification và thống kê toàn hệ thống nhưng không đọc nội dung công việc cá nhân.
- Swagger/OpenAPI, Flyway, Actuator, Dockerfile riêng và database riêng cho từng service có dữ liệu.

## Kiến trúc

```text
React + Nginx (:3000)
          |
          v
Spring Cloud Gateway (:8090 host / :8080 container)
     |          |          |              |
     v          v          v              v
 Auth :8081  Task :8082  AI :8083  Notification :8084
     |          |                         |
 auth_db     task_db               notification_db
```

Frontend chỉ gọi đường dẫn `/api` qua API Gateway. Các service xác thực JWT bằng public key lấy từ JWKS của Auth Service. Private key được sinh một lần và lưu trong Docker volume, nên token không mất hiệu lực chỉ vì container Auth khởi động lại.

Chi tiết: [tài liệu kiến trúc](../docs/architecture/architecture.md).

## Yêu cầu

- Docker Desktop (cách chạy khuyến nghị).
- Hoặc Java 21, Maven 3.9+, Node.js 20+ và PostgreSQL 17 để chạy thủ công.

## Chạy toàn bộ bằng Docker Compose

1. Tạo file môi trường:

   ```powershell
   Copy-Item .env.example .env
   ```

2. Sửa `POSTGRES_PASSWORD`. Có thể giữ `AI_ENABLED=false` để dùng bộ phân tích cục bộ, không cần API key. Muốn dùng AI provider tương thích OpenAI, cấu hình `AI_API_KEY`, `AI_BASE_URL`, `AI_MODEL` và đặt `AI_ENABLED=true`.

   Bản cấu hình cục bộ đã chuẩn bị sẵn `.env`: chỉ thay `AI_API_KEY` và `AI_MODEL`, sau đó chạy `./scripts/enable-ai.ps1`. Script không in API key ra màn hình, tự tạo lại AI Service và Gateway rồi kiểm tra health check.

3. Để tạo Admin đầu tiên, đặt `BOOTSTRAP_ADMIN_ENABLED=true`, thay email/mật khẩu mạnh trong `.env`, chạy hệ thống một lần rồi đặt lại thành `false`. Bootstrap chỉ chạy khi database chưa có Admin.

4. Build và chạy:

   ```bash
   docker compose up --build -d
   docker compose ps
   ```

5. Truy cập:

   | Thành phần | URL |
   |---|---|
   | Website | http://localhost:3000 |
   | Trang quản trị | http://localhost:3000/admin |
   | API Gateway | http://localhost:8090 |
   | Auth Swagger | http://localhost:8081/swagger-ui/index.html |
   | Task Swagger | http://localhost:8082/swagger-ui/index.html |
   | AI Swagger | http://localhost:8083/swagger-ui/index.html |
   | Notification Swagger | http://localhost:8084/swagger-ui/index.html |

Nếu một cổng bị chiếm, đổi biến tương ứng trong `.env`. Cổng Gateway mặc định là `8090` vì một số máy Windows giữ `8080` cho `AgentService`.

## Chạy phát triển

Backend dùng Java 21:

```bash
mvn clean test
mvn -pl services/auth-service spring-boot:run
```

Frontend:

```bash
cd frontend
npm install
npm run dev
```

Vite chạy tại `http://localhost:5173` và proxy `/api` sang Gateway `http://localhost:8090`.

## Kiểm thử

```bash
# Unit + Spring context tests
mvn clean test

# TypeScript + production bundle
cd frontend
npm run build
cd ..

# Khi Docker Compose đang chạy
$env:ADMIN_EMAIL="admin@flowmate.local"
$env:ADMIN_PASSWORD="<mat-khau-admin-trong-env>"
node scripts/e2e.mjs
node scripts/board-e2e.mjs

# UI smoke trên Microsoft Edge (thư mục phải chứa package playwright)
$env:CODEX_NODE_MODULES="<duong-dan-node_modules>"
node scripts/ui-smoke.mjs
node scripts/team-ui-smoke.mjs
node scripts/board-ui-smoke.mjs

# Chỉ kiểm tra frontend nhóm với API giả lập, không thay cho E2E PostgreSQL
node scripts/team-ui-mock-smoke.mjs
```

Bộ E2E kiểm tra đăng ký/JWT/refresh/logout, đổi mật khẩu và thu hồi phiên cũ, cô lập dữ liệu giữa hai tài khoản, hồ sơ, cài đặt, CRUD danh mục/nhãn/mục tiêu, task/checklist/Kanban, dự án nhóm/lời mời/giao việc, dashboard, toàn bộ luồng AI, reminder tự động, notification và các đầu vào không hợp lệ. Khi cung cấp biến `ADMIN_EMAIL`/`ADMIN_PASSWORD`, bộ test còn kiểm tra RBAC, xác thực lại, vòng đời tài khoản, quản lý phiên, audit log và dashboard vận hành. `board-e2e.mjs` kiểm tra mẫu board, board gần đây, board/list/card, ACL, chat, bình luận, checklist, tệp, copy/archive/transfer; `board-ui-smoke.mjs` thao tác qua Edge thật.

Xem [hướng dẫn kiểm thử](../docs/testing/testing.md) và [danh sách API](../docs/api/endpoints.md).

## Dữ liệu và bảo mật

- Không hard-code tài khoản, mật khẩu hoặc API key. Admin đầu tiên chỉ được tạo từ biến môi trường do người vận hành chủ động bật.
- `.env` bị Git bỏ qua; chỉ `.env.example` được lưu.
- Mật khẩu dùng BCrypt; refresh token chỉ lưu SHA-256 hash.
- Chỉ có `USER` và `ADMIN`; khách là trạng thái chưa đăng nhập, không phải bản ghi vai trò.
- Admin không được xem nội dung task cá nhân; các màn hình quản trị chỉ dùng metadata và số liệu tổng hợp.
- Mỗi service chỉ truy cập database của chính nó.
- Task cá nhân được giới hạn theo `userId` lấy từ JWT; API nhóm kiểm tra tư cách thành viên dự án và quyền OWNER/MEMBER ở từng thao tác.
- Lời mời nhóm hiện hiển thị trong ứng dụng theo email tài khoản; chưa gửi email ra ngoài hoặc xác minh email.
- Tệp card hiện lưu trong `task_db` để thử nghiệm local; cần chuyển sang object storage và bổ sung quota/quét tệp trước khi triển khai quy mô lớn.
- Chưa bật bảng `PRIVATE` vì API task cũ chưa thực thi quyền theo board; chưa có PayOS/Premium, Google OAuth hay Botpress. Xem [tiến độ tích hợp GCom](../docs/plan/gcom-integration.md).
- Dừng hệ thống bằng `docker compose down`; thêm `-v` chỉ khi thực sự muốn xóa toàn bộ dữ liệu local.

## Tài liệu

- [Kiến trúc và luồng hệ thống](../docs/architecture/architecture.md)
- [Database](../docs/database/schema.md)
- [API](../docs/api/endpoints.md)
- [Kiểm thử](../docs/testing/testing.md)
- [Kế hoạch thực hiện 5 tuần](../docs/plan/5-week-plan.md)
- [Triển khai môi trường thực tế](../docs/deployment/production.md)

## Kiểm tra nhanh trước khi chạy

Trên Windows, dùng script sau để tự chọn JDK 21, chạy toàn bộ unit/context test,
build frontend, kiểm tra dependency bằng `npm audit` và kiểm tra Docker Compose:

```powershell
.\scripts\verify.ps1
```

Muốn dựng container và chạy E2E sau khi Docker Desktop đã hoạt động:

```powershell
.\scripts\verify.ps1 -WithDocker
```

`INTERNAL_SERVICE_KEY` trong `.env` phải là chuỗi ngẫu nhiên tối thiểu 32 ký tự.
Task Service sử dụng khóa này cho hàng đợi đồng bộ reminder bền vững; khóa không
được gửi tới trình duyệt hoặc lưu trực tiếp trong source code.
