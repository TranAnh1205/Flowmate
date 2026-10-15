# Đối chiếu Flowmate với Vikunja và thứ tự hoàn thiện

Ngày rà soát: 2026-10-15. Mốc tham chiếu sản phẩm là [Vikunja](https://github.com/go-vikunja/vikunja), đặc biệt các tài liệu chính thức về [task](https://vikunja.io/help/tasks/), [chia sẻ nhóm](https://vikunja.io/help/sharing-and-teams/), [các view](https://vikunja.io/help/views/), [ngày/nhắc việc](https://vikunja.io/help/dates-and-reminders/) và [nhập/xuất](https://vikunja.io/help/import-and-export/). Đây là so sánh **phạm vi sản phẩm**, không phải yêu cầu sao chép kiến trúc hoặc mọi tính năng của Vikunja.

## Trạng thái xác nhận từ mã nguồn

| Nhóm chức năng | Flowmate hiện tại | Khoảng trống cần bổ sung | Ưu tiên |
|---|---|---|---|
| Công việc cá nhân | CRUD, checklist, nhãn, danh mục, mục tiêu, dashboard, lịch và Kanban; nằm trong `TaskService`, các trang `TasksPage`/`BoardPage`/`CalendarPage` | Giữ hoạt động ổn định khi thêm nhóm; xác nhận E2E không hồi quy | P0 |
| Dự án nhóm | Tạo/lưu trữ dự án, lời mời trong ứng dụng, OWNER/MEMBER, một người thực hiện, tiến độ; `ProjectService` và `ProjectDetailPage` | Chưa có góc nhìn xuyên dự án “Việc của tôi”; task nhóm không xuất hiện trong dashboard/lịch/reminder/AI cá nhân | P0 |
| Danh tính và lời mời | Khớp lời mời bằng email trong JWT; Auth cấp token ngay sau đăng ký và chưa xác minh sở hữu hộp thư | Xác minh email trước khi xem/chấp nhận lời mời; chặn cả access token/refresh token cấp trước xác minh; thử lạm dụng bằng hai tài khoản | P0 |
| An toàn tài khoản | Có đổi mật khẩu, phiên đăng nhập và khóa tài khoản từ Admin | Chưa thấy tự đặt lại mật khẩu qua email hoặc xác thực hai bước; cần thiết kế trước khi cho người dùng công khai đăng ký | P1 |
| Quyền nhóm | OWNER/MEMBER và kiểm tra tư cách thành viên ở service | Chưa có quyền chỉ xem, quản trị nhóm tái sử dụng cho nhiều dự án, chuyển quyền sở hữu hoặc thu hồi lời mời | P1 |
| Nội dung task nhóm | Tiêu đề, mô tả, trạng thái, ưu tiên, hạn, người thực hiện | Checklist, bình luận, nhật ký thay đổi, nhãn, file đính kèm, quan hệ/phụ thuộc task chưa có trong DTO/bảng/API nhóm | P1 |
| Cách xem task nhóm | Danh sách có phân trang và lọc trạng thái | Kanban, lịch, tìm kiếm/lọc nhiều tiêu chí; Table/Gantt là giai đoạn sau, chỉ khi người dùng cần | P1/P2 |
| Thông báo và thời gian | Reminder/thông báo cho task cá nhân; `recurrence_rule` được lưu ở task cá nhân | Chưa có reminder cho task nhóm; chưa thấy scheduler tạo kỳ tiếp theo từ recurrence; email notification chưa có | P1 |
| Di chuyển dữ liệu | Chưa thấy API/UI nhập hoặc xuất task | Xuất dữ liệu của người dùng/dự án trước, rồi nhập CSV với kiểm tra lỗi và quyền | P2 |
| Tích hợp bên ngoài | AI là tích hợp hiện có | CalDAV, webhook và API token cho bên thứ ba là tùy chọn giai đoạn sau; không nên làm trước luồng nhóm cốt lõi | P2 |
| Sẵn sàng vận hành | Docker Compose, health, Flyway, một số test unit/JDBC/E2E | Cần chạy E2E thật trên PostgreSQL, kiểm tra UI nhóm, backup/restore, CI và TLS trước khi công khai | P0 |

Các kết luận “chưa có” ở đây dựa trên controller, DTO, migration, route frontend và test hiện tại; chưa thay cho kiểm thử runtime. Vikunja có phạm vi rộng hơn và một số chức năng nâng cao thuộc gói Pro, nên không coi toàn bộ repo của họ là tiêu chí bắt buộc cho Flowmate.

Các mục Vikunja khác đã được phân loại để tránh bỏ sót: **saved filters** xuyên dự án và tìm kiếm task nhóm thuộc P1; **quick add** của Flowmate mới có hướng AI xem trước cho task cá nhân, chưa có nhập nhanh tương đương cho nhóm (P2); **webhook, CalDAV, API token, ứng dụng desktop/mobile riêng** là tích hợp tùy chọn P2; **trợ năng bàn phím/screen reader** hiện chưa có bộ test xác nhận, cần thêm vào vòng UI smoke P1. Mức ưu tiên là quyết định sản phẩm của Flowmate, không phải đánh giá các tính năng này kém quan trọng trong Vikunja.

## Lộ trình theo luồng người dùng

1. **P0 — Luồng sử dụng thật:** A tạo dự án, mời B đã xác minh email, giao task; B thấy task cả ở dự án và “Việc của tôi”, có nhắc hạn; A thấy tiến độ. Giữ dữ liệu cá nhân tách biệt và quyền truy cập nhóm đúng.
2. **P0 — Cổng kiểm thử:** test đơn vị/JDBC, E2E qua Gateway + PostgreSQL, UI smoke cho nhóm; sửa lỗi phát hiện; chạy lại toàn bộ sau mỗi thay đổi.
3. **P1 — Cộng tác:** bình luận, activity log, checklist/nhãn/file cho task nhóm, quyền chỉ xem và thu hồi lời mời.
4. **P1 — Khả dụng:** Kanban/lịch/tìm kiếm cho nhóm, thông báo nhóm, tác vụ lặp có scheduler.
5. **P2 — Mở rộng theo nhu cầu:** Table/Gantt, nhóm tái sử dụng nhiều dự án, import/export, liên kết chia sẻ có kiểm soát.

Không chuyển sang P2 khi P0 chưa vượt qua kiểm thử với hai tài khoản thật và database PostgreSQL. Chi tiết test case và tình trạng chạy: [team-project-cases.md](../testing/team-project-cases.md).
