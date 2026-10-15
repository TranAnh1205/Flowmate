# Danh sách API

Base URL qua Gateway: `http://localhost:8090/api/v1`. Trừ register/login/refresh, các endpoint yêu cầu `Authorization: Bearer <accessToken>`.

## Auth và người dùng

| Method | Endpoint | Mục đích |
|---|---|---|
| POST | `/auth/register` | Đăng ký |
| POST | `/auth/login` | Đăng nhập |
| POST | `/auth/refresh-token` | Rotate refresh token |
| POST | `/auth/logout` | Thu hồi refresh token |
| POST | `/auth/change-password` | Đổi mật khẩu |
| POST | `/auth/reauthenticate` | Admin xác thực lại, nhận action token 5 phút |
| GET/PUT | `/users/me` | Xem/cập nhật hồ sơ |
| GET/PUT | `/users/me/settings` | Xem/cập nhật cài đặt |

## Quản trị

Tất cả endpoint dưới đây yêu cầu `ROLE_ADMIN`. Ba thao tác thay đổi trạng thái/vai trò/phiên còn yêu cầu header `X-Admin-Action-Token` lấy từ `/auth/reauthenticate`.

| Method | Endpoint | Mục đích |
|---|---|---|
| GET | `/admin/users` | Tìm kiếm, lọc và phân trang tài khoản |
| GET | `/admin/users/{id}` | Metadata chi tiết tài khoản |
| PATCH | `/admin/users/{id}/status` | Tạm khóa/kích hoạt lại tài khoản |
| PATCH | `/admin/users/{id}/role` | Cấp/thu hồi quyền Admin |
| GET | `/admin/users/{id}/sessions` | Danh sách phiên đăng nhập |
| POST | `/admin/users/{id}/sessions/{sessionId}/revoke` | Thu hồi một phiên |
| POST | `/admin/users/{id}/sessions/revoke-all` | Thu hồi toàn bộ phiên |
| GET | `/admin/audit-logs` | Tra cứu nhật ký quản trị |
| GET | `/admin/statistics/users` | Thống kê tài khoản |
| GET | `/admin/statistics/tasks` | Thống kê task toàn hệ thống |
| GET | `/admin/statistics/notifications` | Thống kê reminder/notification |
| GET | `/admin/ai/status` | Trạng thái AI, không trả API key |
| GET | `/admin/system/health` | Health tổng hợp các microservice |

## Task và dữ liệu hỗ trợ

| Method | Endpoint | Mục đích |
|---|---|---|
| POST/GET | `/tasks` | Tạo/list, search và filter |
| GET/PUT/DELETE | `/tasks/{id}` | Chi tiết/cập nhật/xóa mềm |
| PATCH | `/tasks/{id}/status` | Đổi trạng thái |
| PATCH | `/tasks/{id}/complete` | Hoàn thành |
| PATCH | `/tasks/reorder` | Reorder/Kanban |
| GET | `/tasks/today` | Task hôm nay |
| GET | `/tasks/upcoming` | Task sắp đến hạn |
| GET | `/tasks/overdue` | Task quá hạn |
| POST/PUT/PATCH/DELETE | `/tasks/{taskId}/subtasks/**` | Checklist |
| GET/POST/PUT/DELETE | `/categories/**` | Danh mục |
| GET/POST/PUT/DELETE | `/tags/**` | Nhãn |
| GET/POST/PUT/DELETE | `/goals/**` | Mục tiêu |
| GET | `/statistics/dashboard` | Dashboard cá nhân |

## Dự án nhóm

| Method | Endpoint | Mục đích |
|---|---|---|
| GET/POST | `/projects` | Danh sách dự án đã tham gia / tạo dự án |
| GET/PUT | `/projects/{projectId}` | Chi tiết / cập nhật dự án; PUT yêu cầu OWNER |
| GET | `/projects/invitations` | Lời mời theo email trong JWT |
| POST | `/projects/{projectId}/invitations` | OWNER mời bằng email tài khoản |
| POST | `/projects/invitations/accept` | Chấp nhận lời mời qua `invitationId` |
| GET | `/projects/{projectId}/members` | Thành viên dự án |
| DELETE | `/projects/{projectId}/members/{memberId}` | OWNER loại thành viên |
| DELETE | `/projects/{projectId}/members/me` | Thành viên rời dự án |
| GET/POST | `/projects/{projectId}/tasks` | Danh sách phân trang / tạo công việc nhóm |
| GET/PUT/DELETE | `/projects/{projectId}/tasks/{taskId}` | Chi tiết / cập nhật / xóa mềm theo quyền |
| PATCH | `/projects/{projectId}/tasks/{taskId}/status` | Đổi trạng thái, yêu cầu `version` hiện tại |

Vai trò nhóm `OWNER` và `MEMBER` độc lập với `USER`/`ADMIN` của hệ thống. Các API nhóm yêu cầu thành viên dự án; chủ dự án quản lý lời mời, dự án và thành viên. Người được giao hoặc người tạo có thể cập nhật công việc; thành viên không được tự giao việc cho người khác.

## AI

| Method | Endpoint | Mục đích |
|---|---|---|
| POST | `/ai/parse-task` | Câu tự nhiên → task preview |
| POST | `/ai/breakdown-task` | Chia task thành tối đa 10 bước |
| POST | `/ai/suggest-priority` | Gợi ý ưu tiên |
| POST | `/ai/daily-plan` | Lập lịch làm việc trong ngày |
| POST | `/ai/weekly-summary` | Tóm tắt hiệu suất tuần |

## Reminder và notification

| Method | Endpoint | Mục đích |
|---|---|---|
| POST | `/reminders` | Tạo reminder trực tiếp |
| PUT/DELETE | `/reminders/{id}` | Cập nhật/xóa reminder |
| PUT/DELETE | `/reminders/task/{taskId}` | Đồng bộ reminder theo task |
| GET | `/notifications` | Danh sách phân trang |
| GET | `/notifications/unread-count` | Số chưa đọc |
| PATCH | `/notifications/{id}/read` | Đánh dấu đã đọc |
| PATCH | `/notifications/read-all` | Đọc tất cả |

Schema chi tiết và ví dụ request có thể xem trực tiếp trong Swagger của từng service. Error nghiệp vụ/validation dùng cấu trúc chung gồm `timestamp`, `status`, `error`, `code`, `message`, `path`, `details`.
