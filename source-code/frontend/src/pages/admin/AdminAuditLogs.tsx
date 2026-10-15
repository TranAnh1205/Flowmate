import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { ChevronLeft, ChevronRight, FileClock } from "lucide-react";
import { getAuditLogs } from "../../admin/admin-api";
import { getApiErrorMessage } from "../../api";

const dateTime = new Intl.DateTimeFormat("vi-VN", {
  dateStyle: "short",
  timeStyle: "medium",
});

const actions = [
  "USER_SUSPENDED",
  "USER_ACTIVATED",
  "USER_ROLE_CHANGED",
  "USER_SESSIONS_REVOKED",
  "USER_SESSION_REVOKED",
  "ADMIN_LOGIN_SUCCESS",
  "ADMIN_LOGIN_FAILED",
  "AUDIT_LOG_VIEWED",
];

export default function AdminAuditLogs() {
  const [page, setPage] = useState(0);
  const [action, setAction] = useState("");
  const [result, setResult] = useState("");
  const logs = useQuery({
    queryKey: ["admin", "audit", page, action, result],
    queryFn: () => getAuditLogs({ page, size: 20, action, result }),
  });

  return (
    <section>
      <div className="admin-page-heading">
        <div>
          <span className="eyebrow">APPEND-ONLY</span>
          <h1>Nhật ký quản trị</h1>
          <p>
            Theo dõi thao tác nhạy cảm. Nhật ký không có chức năng sửa hoặc xóa.
          </p>
        </div>
        <span className="admin-count-pill">
          <FileClock size={16} /> {logs.data?.totalElements ?? "—"} bản ghi
        </span>
      </div>

      <div className="admin-filter-panel">
        <select
          value={action}
          onChange={(event) => {
            setAction(event.target.value);
            setPage(0);
          }}
          aria-label="Lọc hành động audit"
        >
          <option value="">Mọi hành động</option>
          {actions.map((value) => (
            <option key={value} value={value}>
              {labelAction(value)}
            </option>
          ))}
        </select>
        <select
          value={result}
          onChange={(event) => {
            setResult(event.target.value);
            setPage(0);
          }}
          aria-label="Lọc kết quả audit"
        >
          <option value="">Mọi kết quả</option>
          <option value="SUCCESS">Thành công</option>
          <option value="FAILED">Thất bại</option>
        </select>
      </div>

      {logs.error && (
        <div className="admin-alert error">
          {getApiErrorMessage(logs.error, "Không thể tải audit log.")}
        </div>
      )}

      <div className="admin-table-wrap">
        <table className="admin-table audit-table">
          <thead>
            <tr>
              <th>Thời gian</th>
              <th>Hành động</th>
              <th>Admin</th>
              <th>Đối tượng</th>
              <th>Lý do</th>
              <th>Kết quả</th>
              <th>IP</th>
            </tr>
          </thead>
          <tbody>
            {logs.data?.content.map((log) => (
              <tr key={log.id}>
                <td>{dateTime.format(new Date(log.createdAt))}</td>
                <td>
                  <strong>{labelAction(log.action)}</strong>
                </td>
                <td className="mono-cell">{log.actorUserId.slice(0, 8)}…</td>
                <td className="mono-cell">
                  {log.targetId ? `${log.targetId.slice(0, 8)}…` : "Hệ thống"}
                </td>
                <td>{log.reason || "—"}</td>
                <td>
                  <span className={`audit-result ${log.result.toLowerCase()}`}>
                    {log.result}
                  </span>
                </td>
                <td>{log.ipAddress || "—"}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {!logs.isLoading && logs.data?.content.length === 0 && (
          <div className="admin-empty">
            <FileClock size={38} />
            <strong>Chưa có nhật ký phù hợp</strong>
          </div>
        )}
      </div>

      {!!logs.data?.totalPages && logs.data.totalPages > 1 && (
        <div className="admin-pagination">
          <button
            className="ghost"
            disabled={page === 0}
            onClick={() => setPage((value) => Math.max(0, value - 1))}
          >
            <ChevronLeft />
          </button>
          <span>
            Trang {page + 1} / {logs.data.totalPages}
          </span>
          <button
            className="ghost"
            disabled={page + 1 >= logs.data.totalPages}
            onClick={() => setPage((value) => value + 1)}
          >
            <ChevronRight />
          </button>
        </div>
      )}
    </section>
  );
}

function labelAction(action: string) {
  const labels: Record<string, string> = {
    USER_SUSPENDED: "Tạm ngừng tài khoản",
    USER_ACTIVATED: "Kích hoạt tài khoản",
    USER_ROLE_CHANGED: "Thay đổi quyền",
    USER_SESSIONS_REVOKED: "Thu hồi toàn bộ phiên",
    USER_SESSION_REVOKED: "Thu hồi một phiên",
    ADMIN_LOGIN_SUCCESS: "Admin đăng nhập",
    ADMIN_LOGIN_FAILED: "Admin đăng nhập thất bại",
    AUDIT_LOG_VIEWED: "Xem audit log",
  };
  return labels[action] || action;
}
