# Kế hoạch mở rộng Flowmate theo mô hình cộng tác GCom

Ngày đối chiếu: 2026-10-15. Nguồn tham khảo: báo cáo GCom của nhóm (mục 2.2, sơ đồ dữ liệu 3.2.2, giao diện 4.3 và bảng kiểm thử 4.3), cùng trang công khai https://gcomproject.onrender.com/. Báo cáo là tài liệu tham khảo chức năng, không phải đặc tả đủ để sao chép triển khai hoặc bằng chứng mọi luồng trên bản deploy còn hoạt động.

## Tiến độ thực tế (2026-10-15)

- **Đã chạy và kiểm thử local:** migration V4/V5/V6/V7 giữ task cũ; board/list/card nhóm, tạo/sửa và sao chép board (cột, thẻ đang hoạt động, checklist, nhãn; không sao chép bình luận/tệp/chat), thêm/đổi thứ tự/đổi tên/xóa list tùy chỉnh trống, tạo/sửa/di chuyển/sao chép/chuyển board/lưu trữ/khôi phục card; ngày bắt đầu/hạn hoàn thành có kiểm tra thứ tự; bình luận, checklist, nhãn cơ bản, theo dõi card, tệp 5 MB, activity, chat có lưu; giao diện Board/Table/Calendar/Dashboard, tìm thẻ và liên kết chia sẻ nội bộ. Ba mẫu board cơ bản/sprint/nội dung và danh sách board đã xem gần đây đã qua E2E API. Docker, 22 test task-service và Edge board UI smoke đều đạt.
- **Mới tương đương một phần:** chat và board dùng polling 3 giây (chưa có SSE/Socket.IO); tệp lưu BYTEA trong PostgreSQL local (chưa có object storage); thống kê board là tổng hợp cơ bản phía client; board role mới có cấu trúc dữ liệu, chưa có luồng quản lý đầy đủ. `PRIVATE` bị chặn có chủ đích vì API task cũ chưa áp quyền theo board. Comment/chat chưa có phân trang/giới hạn tốc độ riêng.
- **Lưu ý vận hành:** Docker Desktop 4.53.0 trên Windows build 26200 bị lỗi socket tạm `dockerInference` (Windows báo 1920). Ngày 2026-10-15, khi Docker đã dừng, xóa đúng socket 0 byte này qua WSL giúp Engine khởi động và migration/E2E chạy được. Đây là cách phục hồi tạm thời, chưa xác nhận sửa vĩnh viễn; không reset Docker volumes/images. Các bản lưu thư mục socket cũ vẫn được giữ nguyên.
- **Chưa triển khai:** xóa/lưu trữ board có dữ liệu, quản lý thành viên board và quyền riêng tư; email nhắc hạn theo GCom; Google OAuth; Botpress; PayOS/Premium, giao dịch và admin doanh thu. Bản sao board không chứa bình luận/tệp/chat hoặc thẻ đã lưu trữ. Chia sẻ card hiện chỉ là liên kết nội bộ cho thành viên có quyền. Không coi các mục này là đã hoàn thành.
- **Cấu hình dịch vụ ngoài:** chủ dự án xác nhận chưa có khóa; vì vậy local chạy không phụ thuộc PayOS/Google/Botpress/Supabase. Tính năng trả phí hoặc đăng nhập bên thứ ba không được mô phỏng thành giao dịch/đăng nhập thật.

## Mục tiêu và ranh giới

Giữ nguyên công việc cá nhân. Theo yêu cầu mới, mục tiêu là **toàn bộ chức năng GCom xác định được từ báo cáo và giao diện công khai**, không chỉ ba nhóm cộng tác. Tích hợp bên thứ ba được xây dựng với cấu hình tùy chọn; không được báo là chạy thực tế nếu chưa có khóa, webhook và kiểm thử với tài khoản của chủ dự án. Những hành vi nằm sau đăng nhập nhưng báo cáo không mô tả cần được xác nhận khi có tài khoản GCom thử nghiệm, không tự đoán.

## Danh mục đầy đủ và tình trạng ban đầu

| Nhóm chức năng GCom | Flowmate trước khi mở rộng | Việc cần làm để tương đương |
| --- | --- | --- |
| Tài khoản, hồ sơ, đăng ký/đăng nhập/đăng xuất, phân quyền | Đã có tài khoản/JWT, hồ sơ cơ bản và quản trị | Kiểm thử luồng tương đương; thêm Google OAuth nếu có client ID/secret và domain hợp lệ. |
| Workspace, thành viên, lời mời, quyền | Đã có dự án nhóm OWNER/MEMBER và lời mời | Mở rộng quản lý board và quyền ADMIN/EDITOR/VIEWER; bổ sung xử lý mời/rút quyền nhất quán. |
| Board: tạo/sửa/xóa/lưu trữ, nền, visibility, thành viên, template | Chưa có board nhóm | Board nhiều cái trong một dự án nhóm, ACL và cài đặt; template/mẫu và thao tác sao chép board. |
| List: tạo/sửa/xóa, đổi thứ tự | Chưa có | List có vị trí, kéo thả, kiểm soát chuyển card trước khi xóa. |
| Card: CRUD, kéo thả, hạn/start date, hoàn thành, assignee/members, nhãn, checklist, theo dõi, sao chép/chuyển/lưu trữ/chia sẻ | Task nhóm có CRUD, assignee, hạn, trạng thái, ưu tiên | Task nhóm làm card; bổ sung vị trí, người tham gia, nhãn, checklist, watch, copy/move/archive/share theo quyền. |
| Bình luận, tệp, activity/history | Chưa có cho task nhóm | Dữ liệu bền vững, phân trang, quyền truy cập, lưu tệp an toàn, nhật ký hoạt động. |
| Messenger/chat board và cập nhật realtime | Chưa có | Chat bền vững và kênh sự kiện xác thực; kiểm thử hai trình duyệt và thu hồi quyền. |
| Board view, Table, Calendar, dashboard/thống kê, tìm kiếm/Recent | Board/Calendar cá nhân và dashboard tổng quát đã có | Thêm các chế độ xem theo dự án/board, số liệu tiến độ, lọc/tìm kiếm và lịch sử board gần đây. |
| Nhắc hạn và thông báo email | Có reminder/notification nội bộ | Thêm email có cấu hình SMTP/provider, lịch gửi chống trùng, tùy chọn nhận thông báo và theo dõi lỗi. |
| Trợ lý ảo Botpress | Có AI assistant của Flowmate, chưa phải Botpress | Quyết định giữ AI hiện có hay nhúng Botpress; nếu yêu cầu giống GCom, tích hợp Botpress bằng khóa riêng và kiểm soát dữ liệu gửi đi. |
| Premium/giới hạn gói, PayOS, webhook, giao dịch | Chưa có | Catalog gói, quota, đơn hàng, webhook xác thực/idempotent, đối soát, hoàn/hủy và màn hình thanh toán. Cần tài khoản PayOS thật để kích hoạt. |
| Admin: người dùng, giao dịch, doanh thu, thống kê | Đã có quản lý người dùng/audit/stats cơ bản | Bổ sung giao dịch, gói dịch vụ, số liệu workspace/board và kiểm soát quyền admin. |

Trạng thái bảng trên là **đối chiếu trước khi triển khai**, không có nghĩa các mục “việc cần làm” đã hoàn thành. Không sao chép nguyên stack Node/MongoDB của GCom; Flowmate tiếp tục Java/Spring, PostgreSQL và React.

## Đối chiếu miền dữ liệu

| GCom trong báo cáo | Flowmate hiện tại | Quyết định triển khai |
| --- | --- | --- |
| Workspace chứa thành viên và nhiều board | `team_projects`, `team_project_members`, lời mời | Giữ `team_projects` làm **không gian cộng tác tương đương workspace**; giữ URL/API dự án để không phá dữ liệu cũ. Một dự án nhóm chứa nhiều board. Có thể đổi nhãn giao diện thành “Không gian/Dự án” sau khi kiểm thử, không đổi bảng dữ liệu vội. |
| Board và thành viên/phân quyền board | Chưa có | Thêm `project_boards` và `board_members`/vai trò ADMIN, EDITOR, VIEWER; chủ dự án luôn có quyền quản trị. Thành viên dự án hiện hữu mặc định được vào board mặc định. Không cho người ngoài dự án truy cập chỉ nhờ biết ID. |
| List có thứ tự | Trạng thái task `TODO`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED` | Thêm `board_lists` với thứ tự bền vững. Mỗi board mặc định có Cần làm, Đang làm, Hoàn thành, Đã hủy; không bỏ sót task `CANCELLED`. Danh sách tùy chỉnh không tự đổi trạng thái nghiệp vụ. |
| Card, task và kéo thả | `team_project_tasks` có assignee, hạn, ưu tiên, version | **Một task nhóm là một card**, không nhân bản nội dung. Thêm `board_id`, `list_id`, `position` vào task; backfill toàn bộ task cũ theo trạng thái. Di chuyển + đổi trạng thái mặc định + ghi activity cùng một transaction, có kiểm tra version để tránh ghi đè đồng thời. |
| Card comment, attachment, activity | Chưa có | Bảng riêng cho bình luận, metadata tệp, event bất biến; chỉ thành viên đủ quyền được xem/tải. |
| Card checklist, nhãn, nhiều thành viên | Một assignee, chưa có checklist/nhãn | Nằm trong giai đoạn card nâng cao; giữ một người chịu trách nhiệm chính, thêm người theo dõi/tham gia, checklist item, nhãn khi luồng cơ bản ổn định. |
| Message theo board | Chưa có | Lưu message theo board; người gửi phải thuộc board và còn quyền tại thời điểm gửi/đọc. |

## Các giai đoạn có thể bàn giao và kiểm thử

### 0. Cố định nền tảng và dữ liệu

- Chụp baseline API/UI và số lượng dự án, thành viên, task; giữ bản sao lưu `task_db` trước migration. Bản sao lưu cục bộ đã tạo: `artifacts/backups/task_db-pre-gcom-20261015.sql` và bản trước V7 `artifacts/backups/task_db-pre-start-at-20261015.dump`.
- Xác nhận phép ánh xạ `team_projects` → workspace với người dùng; không làm thay đổi chức năng task cá nhân.
- Viết kiểm thử migration trên DB có task đủ cả bốn trạng thái và hai dự án khác nhau.

**Đạt khi:** bản sao lưu đọc được, migration chạy lặp an toàn theo Flyway, số lượng task trước/sau bằng nhau, mọi task cũ hiển thị trên board mặc định.

### 1. Board/list/card lõi

- Flyway migration cộng thêm board, list, quyền board và vị trí card; backfill. Chưa xóa/cập nhật phá hủy bảng cũ.
- API CRUD board/list, tạo/sửa/xóa mềm card, sắp xếp list/card, chuyển card trong/cùng board. Giới hạn đầu vào; transaction và optimistic locking. Không xóa board/list đang chứa card nếu chưa có thao tác chuyển rõ ràng.
- UI trong trang dự án nhóm: danh sách board, cột có thứ tự, kéo thả có phản hồi khi lỗi/xung đột; vẫn mở chi tiết task và giao việc như cũ.
- Mặc định project OWNER = board ADMIN; thành viên dự án hiện có = EDITOR, có thể hạ thành VIEWER. Phân quyền được kiểm tra ở server cho từng endpoint.

**Đạt khi:** hai tài khoản cùng dự án thấy cùng thứ tự sau tải lại; di chuyển sai project/board trả lỗi; VIEWER không thể sửa; task cá nhân và API cũ không hồi quy.

### 2. Chi tiết card và cộng tác bất đồng bộ

- Bình luận tạo/sửa/xóa theo chủ sở hữu hoặc quản trị; activity chỉ ghi sự kiện hệ thống (tạo, đổi trạng thái, di chuyển, gán, hạn, tệp, bình luận) với actor/timestamp và phân trang.
- Tệp: bản local hiện lưu metadata và bytes tối đa 5 MB trong PostgreSQL, tải xuống dưới dạng `application/octet-stream` có kiểm soát quyền. Trước khi triển khai quy mô thực tế, chuyển bytes sang object storage tương thích S3, giữ metadata trong PostgreSQL và bổ sung quota/quét tệp.
- Bổ sung checklist, nhãn và người tham gia card theo sơ đồ/ảnh báo cáo; assignee chính vẫn là một người để tương thích tiến độ và thông báo hiện có.
- Giao diện card gom mô tả, hạn, thành viên, checklist, tệp, bình luận và activity; tránh trộn bình luận người dùng với event hệ thống.

**Đạt khi:** người ngoài board không đọc được comment/tệp/activity; tải tệp đúng quyền, có `Content-Disposition: attachment`; xóa mềm task không làm lộ tệp; lịch sử không bị sửa qua API.

### 3. Chat board và cập nhật gần thời gian thực

- Message lưu DB, phân trang theo cursor; giới hạn độ dài/tốc độ gửi; chỉ thành viên board gửi/đọc. Có xử lý người bị thu hồi quyền và nội dung không được nhúng HTML thực thi.
- Kênh sự kiện xác thực theo board (ưu tiên SSE cho nhận event; REST cho thao tác gửi/ghi) để đồng bộ card/comment/chat, tự kết nối lại từ cursor. Nếu môi trường proxy không hỗ trợ streaming ổn định, dùng polling có giới hạn tần suất như phương án dự phòng, không quảng cáo là real-time tuyệt đối.
- Nếu triển khai nhiều instance, thêm outbox + broker/Redis pub-sub trước khi hứa đồng bộ xuyên instance; Redux/client state một mình không giải quyết đồng bộ nhiều người dùng.

**Đạt khi:** hai trình duyệt cùng board nhận card/comment/message mới mà không refresh; người bị loại khỏi board ngừng nhận sự kiện; reconnect không mất/nhân đôi event; thử nghiệm qua gateway/Docker.

### 4. Các chức năng sản phẩm GCom còn lại

- Giao diện Board/Table/Calendar và dashboard board; tìm kiếm/lọc, Recent, template/nhân bản board và cài đặt nền/visibility.
- Email nhắc hạn, thống kê board/workspace, thông báo, audit/admin, quy trình mời thành viên và theo dõi lỗi.
- Google OAuth và Botpress là tích hợp tùy cấu hình, cần khóa/dịch vụ của chủ dự án; giao diện phải báo rõ khi chưa bật.

**Đạt khi:** các luồng trên có E2E độc lập, các tính năng không có cấu hình ngoài hoạt động ở chế độ nội bộ hoặc hiển thị “chưa cấu hình” rõ ràng, không có nút giả.

### 5. Premium/PayOS và vận hành thực tế

- Thiết kế gói, giới hạn và quyền lợi có nguồn sự thật tại backend; UI pricing/upgrade. Không áp hạn mức cho dữ liệu cũ nếu chưa có phương án chuyển đổi.
- Tạo giao dịch PayOS, xác thực webhook/checksum, idempotency và trạng thái đơn, xử lý thanh toán lỗi/huỷ, đối soát; admin xem giao dịch/doanh thu.
- Kiểm thử sandbox rồi production sau khi được cấp khóa; lưu bí mật trong biến môi trường, không trong repository. Backup/restore, quan sát lỗi, kiểm thử hiệu năng và quyền truy cập trước khi xem là hoàn tất.

**Đạt khi:** thanh toán sandbox thành công, webhook trùng không cộng quyền lợi hai lần, webhook giả bị từ chối, entitlement đúng sau khi thanh toán, admin đối soát được; production chỉ bật khi người dùng cung cấp cấu hình hợp lệ.

## Chất lượng và lưu ý từ báo cáo

- Báo cáo kiểm thử chủ yếu liệt kê case thành công/validation cho workspace, card, thống kê, người dùng; còn thiếu kiểm thử quyền chéo, đồng thời, upload độc hại, reconnect, backup/restore và khối lượng dữ liệu. Flowmate cần các bài kiểm thử này trước khi coi là chạy thực tế.
- Sơ đồ dữ liệu báo cáo dùng MongoDB và có cả thanh toán/webhook. Flowmate đã dùng PostgreSQL/Flyway và Java Spring; không đổi stack chỉ để giống GCom.
- Một số đoạn mô tả báo cáo không khớp ngữ cảnh (ví dụ màn hình thành viên nói về giỏ hàng), và phần kết luận thừa nhận dữ liệu đôi lúc cập nhật chậm. Cần dùng hành vi đã kiểm thử làm tiêu chí chấp nhận, không dùng nguyên văn mọi câu trong báo cáo làm yêu cầu.
- Không reset Docker volumes hay xóa dữ liệu cũ; mọi migration đều có kiểm thử trên bản sao và đường phục hồi trước khi áp dụng lên DB đang dùng.
