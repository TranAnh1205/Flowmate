import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { ChevronLeft, ChevronRight, Search, UserRound } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { getAdminUsers } from "../../admin/admin-api";
import type { AccountStatus, UserRole } from "../../types";
import { getApiErrorMessage } from "../../api";

const date = new Intl.DateTimeFormat("vi-VN", {
  day: "2-digit",
  month: "2-digit",
  year: "numeric",
});

export default function AdminUsersPage() {
  const [q, setQ] = useState("");
  const [search, setSearch] = useState("");
  const [role, setRole] = useState<UserRole | "">("");
  const [status, setStatus] = useState<AccountStatus | "">("");
  const [page, setPage] = useState(0);
  const navigate = useNavigate();
  const users = useQuery({
    queryKey: ["admin", "users", page, search, role, status],
    queryFn: () => getAdminUsers({ page, size: 20, q: search, role, status }),
  });

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    setPage(0);
    setSearch(q.trim());
  };

  return (
    <section>
      <div className="admin-page-heading">
        <div>
          <span className="eyebrow">QUẢN LÝ TÀI KHOẢN</span>
          <h1>Người dùng</h1>
          <p>
            Khóa, mở khóa và quản lý quyền mà không truy cập dữ liệu cá nhân.
          </p>
        </div>
        <span className="admin-count-pill">
          {users.data?.totalElements ?? "—"} tài khoản
        </span>
      </div>

      <div className="admin-filter-panel">
        <form onSubmit={submit}>
          <Search size={18} />
          <input
            value={q}
            onChange={(event) => setQ(event.target.value)}
            placeholder="Tìm theo tên hoặc email..."
            aria-label="Tìm người dùng"
          />
          <button className="primary small">Tìm kiếm</button>
        </form>
        <select
          value={role}
          onChange={(event) => {
            setRole(event.target.value as UserRole | "");
            setPage(0);
          }}
          aria-label="Lọc theo quyền"
        >
          <option value="">Mọi quyền</option>
          <option value="USER">USER</option>
          <option value="ADMIN">ADMIN</option>
        </select>
        <select
          value={status}
          onChange={(event) => {
            setStatus(event.target.value as AccountStatus | "");
            setPage(0);
          }}
          aria-label="Lọc theo trạng thái tài khoản"
        >
          <option value="">Mọi trạng thái</option>
          <option value="ACTIVE">Đang hoạt động</option>
          <option value="LOCKED">Bị khóa bảo mật</option>
          <option value="SUSPENDED">Tạm ngừng</option>
          <option value="DEACTIVATED">Đã vô hiệu hóa</option>
        </select>
      </div>

      {users.error && (
        <div className="admin-alert error">
          {getApiErrorMessage(
            users.error,
            "Không thể tải danh sách người dùng.",
          )}
        </div>
      )}

      <div className="admin-table-wrap">
        <table className="admin-table">
          <thead>
            <tr>
              <th>Người dùng</th>
              <th>Quyền</th>
              <th>Trạng thái</th>
              <th>Phiên hoạt động</th>
              <th>Ngày đăng ký</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {users.isLoading &&
              Array.from({ length: 5 }).map((_, index) => (
                <tr key={index} className="table-skeleton">
                  <td colSpan={6}>
                    <span />
                  </td>
                </tr>
              ))}
            {users.data?.content.map((user) => (
              <tr key={user.id}>
                <td>
                  <div className="admin-user-cell">
                    <span className="avatar">
                      {user.fullName.charAt(0).toUpperCase()}
                    </span>
                    <span>
                      <strong>{user.fullName}</strong>
                      <small>{user.email}</small>
                    </span>
                  </div>
                </td>
                <td>
                  <span className={`role-badge ${user.role.toLowerCase()}`}>
                    {user.role}
                  </span>
                </td>
                <td>
                  <StatusBadge status={user.status} />
                </td>
                <td>{user.activeSessions}</td>
                <td>{date.format(new Date(user.createdAt))}</td>
                <td>
                  <button
                    className="ghost small"
                    onClick={() => navigate(`/admin/users/${user.id}`)}
                  >
                    Xem chi tiết
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {!users.isLoading && users.data?.content.length === 0 && (
          <div className="admin-empty">
            <UserRound size={38} />
            <strong>Không tìm thấy người dùng</strong>
            <span>Thử thay đổi từ khóa hoặc bộ lọc.</span>
          </div>
        )}
      </div>

      {!!users.data?.totalPages && users.data.totalPages > 1 && (
        <div className="admin-pagination">
          <button
            className="ghost"
            disabled={page === 0}
            onClick={() => setPage((value) => Math.max(0, value - 1))}
            aria-label="Trang trước"
          >
            <ChevronLeft />
          </button>
          <span>
            Trang {page + 1} / {users.data.totalPages}
          </span>
          <button
            className="ghost"
            disabled={page + 1 >= users.data.totalPages}
            onClick={() => setPage((value) => value + 1)}
            aria-label="Trang sau"
          >
            <ChevronRight />
          </button>
        </div>
      )}
    </section>
  );
}

export function StatusBadge({ status }: { status: AccountStatus }) {
  const labels: Record<AccountStatus, string> = {
    ACTIVE: "Đang hoạt động",
    LOCKED: "Bị khóa",
    SUSPENDED: "Tạm ngừng",
    DEACTIVATED: "Vô hiệu hóa",
  };
  return (
    <span className={`account-status ${status.toLowerCase()}`}>
      {labels[status]}
    </span>
  );
}
