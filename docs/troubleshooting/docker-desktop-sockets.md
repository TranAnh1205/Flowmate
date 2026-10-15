# Docker Desktop không khởi động vì socket tạm trên Windows

Đã kiểm chứng trên máy này ngày 2026-10-15: Windows 11 Pro build 26200, Docker Desktop 4.53.0, WSL2. Log báo `initializing Inference manager ... dockerInference ... The file cannot be accessed by the system`. `fsutil reparsepoint query` trả lỗi 1920. Socket 0 byte có thể còn lại sau khi Docker dừng; việc đổi tên thư mục `run`/`docker-secrets-engine` chỉ xử lý một lần và có thể làm lỗi chuyển sang socket khác.

## Phục hồi tạm thời đã kiểm chứng

1. Thoát Docker Desktop hoàn toàn, đợi không còn tiến trình `Docker Desktop` và `com.docker.backend`. **Không chọn Reset to factory defaults** và không xóa Docker volumes/images.
2. Trong PowerShell, kiểm tra file socket đúng đường dẫn và kích thước 0 byte:

   ```powershell
   # Thay <WindowsUser> bằng tên thư mục hồ sơ Windows trên máy của bạn.
   wsl -d docker-desktop -u root -- stat -c '%n %s %F' '/mnt/host/c/Users/<WindowsUser>/AppData/Local/Docker/run/dockerInference'
   ```

3. Chỉ khi file đúng là `dockerInference` 0 byte và Docker đã dừng, xóa **riêng file tạm này** qua WSL (không dùng `rm -rf`, không xóa cả thư mục):

   ```powershell
   wsl -d docker-desktop -u root -- rm -- '/mnt/host/c/Users/<WindowsUser>/AppData/Local/Docker/run/dockerInference'
   ```

4. Khởi động Docker Desktop, đợi Engine chạy rồi kiểm tra `docker info` và `docker ps`. Trên máy này, Engine 29.0.1 đã chạy lại; Flowmate và migration V6 hoạt động.

Nếu log chuyển sang socket `docker-secrets-engine/engine.sock` hoặc `userAnalyticsOtlpHttp.sock`, **dừng lại và kiểm tra đúng file mới trước khi xử lý**; đừng xóa hàng loạt. Nếu WSL không có đường dẫn `/mnt/host/c`, cũng dừng lại. Đây là workaround, không phải bản sửa vĩnh viễn. Các thư mục `.stale-*` đã đổi tên trước đó được giữ nguyên, không cần xóa để Flowmate chạy.

Vấn đề cùng dấu hiệu trên Windows build 26200 được mô tả tại [Docker desktop-feedback #460](https://github.com/docker/desktop-feedback/issues/460) và [#527](https://github.com/docker/desktop-feedback/issues/527). Báo cáo người dùng cho thấy tắt Model Runner hoặc factory reset không nhất thiết ngăn socket được tạo lại; chưa có cơ sở khẳng định cập nhật Docker sẽ chữa dứt điểm trên máy này.
