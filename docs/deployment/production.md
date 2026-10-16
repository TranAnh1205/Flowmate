# Triển khai môi trường thực tế

## Phạm vi

Cấu hình Docker Compose hiện phù hợp cho máy chủ đơn hoặc môi trường staging. Khi công khai trên Internet, phải đặt reverse proxy HTTPS phía trước frontend và không mở trực tiếp cổng database.

## Chuẩn bị

1. Cài Docker Engine/Desktop và Docker Compose.
2. Sao chép `.env.example` thành `.env` tại gốc repository.
3. Thay toàn bộ giá trị mẫu:
   - `POSTGRES_PASSWORD`: mật khẩu ngẫu nhiên mạnh.
   - `INTERNAL_SERVICE_KEY`: tối thiểu 32 ký tự ngẫu nhiên.
   - `BOOTSTRAP_ADMIN_PASSWORD`: tối thiểu 12 ký tự; chỉ bật bootstrap một lần.
   - `CORS_ALLOWED_ORIGIN_PATTERNS`: domain HTTPS thật, không dùng wildcard localhost.
   - `AI_API_KEY`, `AI_MODEL`, `AI_BASE_URL`: chỉ cấu hình khi bật provider thật.
4. Không đưa `.env`, private key JWT hoặc bản sao database vào Git.

## Kiểm tra và khởi động

```powershell
# Chạy từ gốc repository
.\scripts\verify.ps1 -WithDocker
```

Sau lần tạo Admin đầu tiên, đặt `BOOTSTRAP_ADMIN_ENABLED=false` và chạy lại `docker compose up -d auth-service`.

## Tiêu chí chấp nhận

- Tất cả container ở trạng thái `healthy`.
- `http://localhost:8090/actuator/health` trả `UP`.
- Frontend trả HTTP 200.
- `mvn clean test`, `npm run build`, `npm audit` và `scripts/e2e.mjs` đạt.
- Bảng `flyway_schema_history` của `task_db` có migration V1, V2 và V3 thành công.
- `reminder_sync_commands` trở về 0 sau khi Notification Service hoạt động bình thường.

## Vận hành

- Sao lưu ba volume database và volume `auth-key-data`; mất private key sẽ làm access token hiện tại mất hiệu lực.
- Theo dõi health, log lỗi 5xx, số command reminder đang chờ và dung lượng database.
- Khi build lại service phụ thuộc, chạy lại toàn bộ Compose hoặc làm mới AI/Gateway để tránh connection pool giữ địa chỉ container cũ.
- Không dùng `docker compose down -v` trên môi trường có dữ liệu cần giữ.

## Giới hạn trước khi triển khai Internet công khai

- Cần reverse proxy TLS, domain và chứng chỉ HTTPS.
- Nên chuyển refresh token sang secure HttpOnly cookie.
- Nên bổ sung rate limiting tập trung, metrics/trace và cảnh báo tự động.
- API AI thật phụ thuộc quota, chi phí và chính sách dữ liệu của provider do người triển khai lựa chọn.
