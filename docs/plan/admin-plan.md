# Phạm vi quản trị đã triển khai

## Mô hình người dùng

- `Guest`: chưa đăng nhập, chỉ truy cập đăng ký/đăng nhập; không phải một role lưu trong database.
- `USER`: quản lý toàn bộ dữ liệu công việc cá nhân của chính mình.
- `ADMIN`: vận hành hệ thống và tài khoản; không đọc nội dung công việc cá nhân.

Không có Staff/Manager/Moderator ở cấp hệ thống. Vai trò OWNER/MEMBER của dự án nhóm được kiểm tra riêng trong Task Service và không cấp quyền Admin.

## Backend

1. RBAC nhất quán tại Gateway và từng microservice bằng claim `roles`/`permissions` trong JWT.
2. Quản lý tài khoản: tìm kiếm, lọc, phân trang, metadata, trạng thái và vai trò.
3. Quản lý session: theo dõi IP/user-agent/thời gian, thu hồi một hoặc tất cả refresh token.
4. Step-up authentication: nhập lại mật khẩu, action token ngẫu nhiên lưu dạng hash và hết hạn sau 5 phút.
5. Guard rail: cấm tự khóa, tự hạ quyền, vô hiệu hóa Admin cuối cùng và cấp quyền cho tài khoản không hoạt động.
6. Audit append-only cho đăng nhập Admin, xem log và mọi thay đổi nhạy cảm.
7. Thống kê được tính tại service sở hữu dữ liệu; Gateway tổng hợp health và không truy cập chéo database.
8. Flyway V2 nâng cấp database đang có mà không xóa dữ liệu người dùng.

## Frontend

1. Route và layout `/admin` riêng, có guard role và trang 403.
2. Dashboard tổng hợp tài khoản, task, notification, AI và tình trạng service.
3. Danh sách tài khoản có tìm kiếm/lọc/phân trang và responsive table.
4. Chi tiết tài khoản, phiên đăng nhập, modal xác thực lại và bắt buộc nhập lý do.
5. Audit log có bộ lọc và phân trang.
6. Trang System giám sát service và cấu hình AI đã che thông tin bí mật.
7. Sidebar desktop/mobile, dark mode và đường quay lại khu vực cá nhân.

## Tiêu chí chấp nhận

- USER gọi API Admin nhận 403; Admin hợp lệ truy cập được.
- Không response nào chứa `passwordHash`, refresh token thô hoặc API key.
- Thao tác nhạy cảm không có/sai/hết hạn action token phải thất bại.
- Trạng thái tài khoản ngăn đăng nhập/refresh; kích hoạt lại cho phép đăng nhập.
- Thu hồi phiên làm refresh token cũ mất hiệu lực.
- Mọi mutation có audit log với actor, target, lý do và thời điểm.
- UI Admin hoạt động ở desktop/mobile, không có lỗi console nghiêm trọng.
- Migration chạy được trên PostgreSQL có dữ liệu cũ; tất cả container healthy và E2E qua Gateway đạt.
