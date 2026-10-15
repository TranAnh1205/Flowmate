# Test case dự án nhóm Flowmate

Ngày bắt đầu kiểm thử: 2026-10-15. Mục tiêu là kiểm tra hành vi hiện có và khóa các lỗi hồi quy trước khi mở rộng theo [bảng đối chiếu Vikunja](../plan/vikunja-gap-analysis.md). “Đạt” chỉ áp dụng cho tầng test đã chạy; không suy diễn H2/JDBC thành PostgreSQL/E2E.

| ID | Ca kiểm thử / kết quả mong đợi | Tầng tự động | Trạng thái |
|---|---|---|---|
| TP-01 | OWNER tạo dự án; người ngoài không đọc được | JDBC, MockMvc, E2E | JDBC và MockMvc đạt; E2E chờ Docker |
| TP-02 | Lời mời chỉ hiện cho đúng email; chuẩn hóa hoa/thường; không tạo trùng | JDBC, MockMvc | JDBC đạt; MockMvc kiểm tra riêng tư và chuẩn hóa email |
| TP-03 | Lời mời chỉ dùng một lần; hết hạn không còn trong inbox và không chấp nhận được | JDBC | Đã thêm test |
| TP-04 | MEMBER không mời/xóa người, không sửa dự án; không giao việc cho thành viên khác; OWNER không thể rời nhóm | JDBC, E2E | JDBC đạt; E2E một phần |
| TP-05 | Người không được giao/không tạo task không sửa trạng thái hoặc xóa task | JDBC | Đã thêm test |
| TP-06 | Người được giao phải thuộc dự án; bị xóa thành viên hoặc tự rời nhóm thì task tự bỏ giao và quyền đọc bị thu hồi | JDBC | Đã thêm và đã có test |
| TP-07 | Phiên bản cũ bị từ chối 409, tránh ghi đè đồng thời | JDBC, E2E | Đã có test; E2E chờ Docker |
| TP-08 | Dự án đã lưu trữ vẫn đọc được nhưng không nhận task/lời mời hay sửa task; lời mời đang chờ không thể chấp nhận; mở lại được | JDBC | Đã thêm test |
| TP-09 | Lọc trạng thái, phân trang, đầu vào sai và xóa mềm không làm lệch số liệu | JDBC | Đã thêm test |
| TP-10 | Hai tài khoản thật qua Gateway, lời mời → giao việc → hoàn thành → gỡ thành viên | E2E PostgreSQL | Script có luồng cơ bản; chưa chạy vì Docker Desktop chưa hoạt động |
| TP-11 | OWNER tạo dự án/mời/giao việc; MEMBER tham gia/hoàn thành; viewport 320/390/768/desktop không tràn ngang, không lỗi runtime | Playwright UI | Đạt với API giả lập; chưa xác nhận trên stack thật |
| TP-12 | Tài khoản chưa xác minh hộp thư không thể xem/chấp nhận lời mời gửi tới email đó | E2E bảo mật | Đã xác nhận đường rủi ro từ mã; chưa có email verification nên chưa thể đạt; E2E chờ stack thật |
| TP-13 | Task nhóm xuất hiện trong “Việc của tôi”, lịch và reminder đúng người | E2E tích hợp | Chưa thể đạt: chức năng chưa có |
| TP-14 | Mất/restart service không mất dữ liệu hoặc làm gửi reminder lặp | E2E vận hành | Chưa kiểm thử |
| TP-15 | Email viết hoa chứa chữ `I` vẫn khớp lời mời khi máy chủ dùng locale `tr-TR` | Unit Auth, MockMvc Task | Tái hiện lỗi trước khi sửa; sau sửa đã đạt |
| TP-16 | Dependency frontend không có cảnh báo bảo mật đã biết; production build tạo được bundle | npm audit, npm run build | Đạt sau khi cập nhật Axios và lockfile |

### TP-12 — kịch bản bắt buộc trước khi mở đăng ký công khai

1. OWNER mời `victim@example.com` khi địa chỉ này chưa có tài khoản Flowmate.
2. Người **không kiểm soát hộp thư** đăng ký địa chỉ đó. Kỳ vọng: tài khoản chưa xác minh không được xem/chấp nhận lời mời và không được cấp quyền dự án qua JWT.
3. Chỉ sau khi chủ hộp thư mở liên kết hoặc nhập mã xác minh còn hiệu lực, tài khoản mới được nhận lời mời; mã dùng lại/hết hạn phải bị từ chối. Kiểm tra thêm đăng nhập và refresh token trước/sau xác minh, để token cũ không vượt qua điều kiện.
4. Với email đã thuộc tài khoản hiện hữu, thử đăng ký trùng phải vẫn bị chặn; đây là ca khác với bước 1–2 và **không** chứng minh email của tài khoản mới là thật.

Bằng chứng hiện tại chỉ ở mã nguồn: `AuthService.register()` trả token ngay, `JwtService` đưa `email` vào JWT nhưng không có `email_verified`, `User` không lưu trạng thái xác minh, và `ProjectService.accept()` khớp lời mời với email JWT. Vì vậy đây là **khoảng trống bảo mật đã xác nhận về thiết kế**, không phải lỗi đã tái hiện trên PostgreSQL. Không dùng test JWT giả lập/MockMvc để tuyên bố TP-12 đạt. Điều kiện khắc phục là thêm xác minh sở hữu email qua kênh gửi thật, trạng thái lưu bền, token có trạng thái đáng tin cậy và kiểm tra ở Task Service; sau đó chạy E2E hai tài khoản theo các bước trên.

## Cách chạy và tiêu chí đọc kết quả

```powershell
Set-Location source-code
./scripts/verify.ps1
```

Nếu `mvn clean test` hỏng ở Spring context vì `Unable to establish loopback connection`, không được coi các test còn lại là đạt. Có thể chạy riêng `ProjectServiceIntegrationTest` bằng JDK 21 để xác nhận JDBC/H2, nhưng vẫn cần chạy lại full suite trên môi trường hoạt động bình thường.

Khi Docker Desktop chạy và `.env` đã cấu hình an toàn:

```powershell
Set-Location source-code
./scripts/verify.ps1 -WithDocker
```

E2E nhóm đã có luồng chính và một số ca lỗi trong `scripts/e2e.mjs`. Chỉ đánh dấu TP-10 đạt sau khi script chạy thành công với PostgreSQL thật; TP-11 chỉ đạt khi Playwright chạy trên UI thật. Lỗi phát hiện phải được tái hiện bằng test trước khi sửa, sau đó chạy lại test và ghi kết quả.

Chạy riêng UI nhóm sau khi frontend, Gateway và các service healthy (cần Microsoft Edge và thư mục có package `playwright`):

```powershell
Set-Location source-code
$env:CODEX_NODE_MODULES="<duong-dan-node_modules-co-playwright>"
node scripts/team-ui-smoke.mjs
```

Script đăng ký hai tài khoản test mới, thao tác qua trình duyệt và ghi ảnh/báo cáo vào `artifacts/team-ui-smoke`; không xóa dữ liệu test đã tạo. Nếu chưa chạy, chỉ có kiểm tra cú pháp, không đánh dấu TP-11 đạt.

Khi Docker chưa hoạt động, có thể kiểm tra riêng giao diện với API giả lập sau `npm run build`:

```powershell
Set-Location source-code
$env:CODEX_NODE_MODULES="<duong-dan-node_modules-co-playwright>"
node scripts/team-ui-mock-smoke.mjs
```

Kết quả lưu ở `artifacts/team-ui-mock-smoke`; đây **không phải** kết quả TP-10 hoặc bằng chứng UI chạy với API thật.

## Nhật ký chạy 2026-10-15

- `ProjectServiceIntegrationTest` (H2/JDBC): 5/5 đạt. Ca TP-02 đã phát hiện email thành viên chưa được chuẩn hóa khi chấp nhận lời mời bằng chữ hoa; đã sửa trong `ProjectService` và chạy lại đạt.
- Auth service unit: 11/11 đạt. Task service unit/JDBC được chọn: 16/16 đạt. AI service unit: 13/13 đạt. Notification service unit: 6/6 đạt (tổng 46 test được chọn).
- `npm run build`: đạt. `node --check scripts/e2e.mjs`: đạt; đây chỉ là kiểm tra cú pháp, chưa chạy HTTP E2E.
- Full `mvn clean test` bằng JDK 21: **49/49 đạt**, gồm Gateway 1, Auth 12, Task 17, AI 13 và Notification 6. Test context của Task dùng HTTP client loại `simple` trong test; test CORS Gateway kiểm tra cấu hình không mở cổng. HTTP preflight thật được thêm vào E2E nhưng chưa chạy.
- `docker compose config --quiet`: đạt. Đã thử mở Docker Desktop; `docker info` vẫn không trả trạng thái engine và WSL `docker-desktop` không duy trì trạng thái Running. Chưa chạy TP-10/TP-11 với PostgreSQL và trình duyệt thật.
- `node --check scripts/team-ui-smoke.mjs` và `node --check scripts/e2e.mjs`: đạt. Log Docker Desktop xác nhận backend dừng khi khởi tạo Secrets Engine, vì vậy không khởi chạy container hay thay đổi volume trong lượt này.
- UI smoke với API giả lập/Edge: đạt 10 khẳng định, không có console/page/network error. Lần đầu phát hiện `input[type=datetime-local]` làm form nhóm tràn ngang ở viewport 390 px (`scrollWidth=445`). Đã chuyển form nhóm thành một cột trên mobile và giới hạn min-width; chạy lại đạt ở 320/390/768 px (scrollWidth bằng viewport). Báo cáo và ảnh: `artifacts/team-ui-mock-smoke`.
- Bổ sung 2 ca JDBC về OWNER/MEMBER/rời nhóm và lời mời chờ khi dự án lưu trữ: `ProjectServiceIntegrationTest` **7/7 đạt**. Chạy lại full `mvn clean test` bằng JDK 21: **51/51 đạt** (Gateway 1, Auth 12, Task 19, AI 13, Notification 6). Đây vẫn là H2/JDBC và context test, chưa xác nhận PostgreSQL/E2E. Docker Desktop/WSL hiện vẫn dừng; chưa can thiệp cấu hình hoặc dữ liệu Docker ngoài workspace.
- Bổ sung `ProjectControllerIntegrationTest` chạy MockMvc + Security Filter + service + H2: chưa đăng nhập trả 401; dữ liệu sai 400; người ngoài không xem dự án/task/lời mời của người khác; người được mời chấp nhận và hoàn thành task. Ca này **1/1 đạt**; full `mvn clean test` chạy lại **52/52 đạt** (Gateway 1, Auth 12, Task 20, AI 13, Notification 6). MockMvc dùng JWT giả lập nên **không xác nhận chữ ký JWT, Gateway, PostgreSQL hoặc trình duyệt thật**; TP-10/TP-11 vẫn chờ stack thật.
- TP-15 phát hiện lỗi chuẩn hóa email phụ thuộc locale hệ điều hành: `INVITE@example.com` thành ký tự `ı` khi dùng `tr-TR`, khiến Auth lưu địa chỉ lệch và Task API không tìm thấy lời mời. Unit Auth và MockMvc Task đều thất bại trước sửa; đã dùng `Locale.ROOT` trong đăng ký, bootstrap Admin và đọc email JWT ở Project Controller. Sau sửa, hai ca đạt; full `mvn clean test` **54/54 đạt** (Gateway 1, Auth 13, Task 21, AI 13, Notification 6). Đây không thay thế xác minh sở hữu email hoặc E2E PostgreSQL.
- TP-16: `npm audit --omit=dev` ban đầu báo 1 cảnh báo high cho Axios 1.18.1; `npm audit` đầy đủ còn 3 cảnh báo ở dependency build. Đã nâng Axios lên 1.20.0 và cập nhật lockfile cho `browserslist`, `baseline-browser-mapping`, `source-map-js`. Sau đó `npm audit` **0 vulnerability**, `npm run build` đạt. Script `scripts/verify.ps1` nay chạy cả audit; chạy script không kèm `-WithDocker` đạt toàn bộ 54/54 test backend, build, audit và `docker compose config --quiet`. Bundle còn cảnh báo kích thước >500 kB, cần tối ưu tải trang nhưng không làm build thất bại. Chưa chạy E2E PostgreSQL.
- Sau nâng Axios, chạy lại `node scripts/team-ui-mock-smoke.mjs`: **10/10 khẳng định đạt**, không có lỗi runtime; viewport 320/390/768 px không tràn ngang. Đây vẫn là API giả lập, không thay thế TP-10 hoặc UI trên stack thật.
