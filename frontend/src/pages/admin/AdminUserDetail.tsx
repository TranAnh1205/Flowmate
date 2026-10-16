import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate, useParams } from "react-router-dom";
import {
  ArrowLeft,
  KeyRound,
  LockKeyhole,
  Monitor,
  RefreshCcw,
  ShieldCheck,
  ShieldOff,
  UserCog,
  X,
} from "lucide-react";
import {
  changeAdminUserRole,
  changeAdminUserStatus,
  getAdminSessions,
  getAdminUser,
  reauthenticateAdmin,
  revokeAdminUserSession,
  revokeAdminUserSessions,
} from "../../admin/admin-api";
import { getApiErrorMessage } from "../../api";
import { useAuth } from "../../auth";
import { StatusBadge } from "./AdminUsersPage";

type SensitiveAction = "SUSPEND" | "ACTIVATE" | "PROMOTE" | "DEMOTE" | "REVOKE";
type SessionSensitiveAction = SensitiveAction | "REVOKE_SESSION";

const dateTime = new Intl.DateTimeFormat("vi-VN", {
  dateStyle: "medium",
  timeStyle: "short",
});

export default function AdminUserDetail() {
  const { userId = "" } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { user: currentUser } = useAuth();
  const [action, setAction] = useState<SessionSensitiveAction | null>(null);
  const [targetSessionId, setTargetSessionId] = useState<string | null>(null);
  const [reason, setReason] = useState("");
  const [password, setPassword] = useState("");
  const [actionError, setActionError] = useState("");
  const [saving, setSaving] = useState(false);
  const [success, setSuccess] = useState("");

  const user = useQuery({
    queryKey: ["admin", "user", userId],
    queryFn: () => getAdminUser(userId),
    enabled: !!userId,
  });
  const sessions = useQuery({
    queryKey: ["admin", "user", userId, "sessions"],
    queryFn: () => getAdminSessions(userId),
    enabled: !!userId,
  });

  const openAction = (value: SessionSensitiveAction, sessionId?: string) => {
    setAction(value);
    setTargetSessionId(sessionId ?? null);
    setReason("");
    setPassword("");
    setActionError("");
    setSuccess("");
  };

  const submitAction = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!action || reason.trim().length < 5) return;
    setSaving(true);
    setActionError("");
    try {
      const auth = await reauthenticateAdmin(password);
      if (action === "SUSPEND" || action === "ACTIVATE") {
        await changeAdminUserStatus(
          userId,
          action === "ACTIVATE" ? "ACTIVE" : "SUSPENDED",
          reason.trim(),
          auth.adminActionToken,
        );
      } else if (action === "PROMOTE" || action === "DEMOTE") {
        await changeAdminUserRole(
          userId,
          action === "PROMOTE" ? "ADMIN" : "USER",
          reason.trim(),
          auth.adminActionToken,
        );
      } else if (action === "REVOKE_SESSION" && targetSessionId) {
        await revokeAdminUserSession(
          userId,
          targetSessionId,
          reason.trim(),
          auth.adminActionToken,
        );
      } else {
        await revokeAdminUserSessions(
          userId,
          reason.trim(),
          auth.adminActionToken,
        );
      }
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["admin", "user", userId] }),
        queryClient.invalidateQueries({ queryKey: ["admin", "users"] }),
        queryClient.invalidateQueries({
          queryKey: ["admin", "statistics", "users"],
        }),
      ]);
      setAction(null);
      setTargetSessionId(null);
      setSuccess("Thao tác quản trị đã được thực hiện và ghi audit log.");
    } catch (error) {
      setActionError(
        getApiErrorMessage(error, "Không thể thực hiện thao tác quản trị."),
      );
    } finally {
      setSaving(false);
    }
  };

  if (user.isLoading) {
    return <div className="admin-loading">Đang tải thông tin tài khoản...</div>;
  }
  if (user.error || !user.data) {
    return (
      <div className="admin-alert error">
        {getApiErrorMessage(user.error, "Không tìm thấy tài khoản.")}
      </div>
    );
  }

  const value = user.data;
  const isSelf = value.id === currentUser?.id;

  return (
    <section>
      <button
        className="ghost back-button"
        onClick={() => navigate("/admin/users")}
      >
        <ArrowLeft size={17} /> Danh sách người dùng
      </button>

      <div className="admin-user-hero">
        <span className="avatar large">
          {value.fullName.charAt(0).toUpperCase()}
        </span>
        <div>
          <span className="eyebrow">CHI TIẾT TÀI KHOẢN</span>
          <h1>{value.fullName}</h1>
          <p>{value.email}</p>
          <div className="user-hero-badges">
            <span className={`role-badge ${value.role.toLowerCase()}`}>
              {value.role}
            </span>
            <StatusBadge status={value.status} />
            {isSelf && <span className="self-badge">Tài khoản của bạn</span>}
          </div>
        </div>
      </div>

      {success && <div className="admin-alert success">{success}</div>}

      <div className="admin-user-layout">
        <article className="admin-panel">
          <div className="panel-title">
            <div>
              <span className="eyebrow">METADATA</span>
              <h2>Thông tin được phép xem</h2>
            </div>
          </div>
          <dl className="admin-detail-list">
            <div>
              <dt>ID</dt>
              <dd>{value.id}</dd>
            </div>
            <div>
              <dt>Múi giờ</dt>
              <dd>{value.timezone}</dd>
            </div>
            <div>
              <dt>Ngày đăng ký</dt>
              <dd>{dateTime.format(new Date(value.createdAt))}</dd>
            </div>
            <div>
              <dt>Đăng nhập gần nhất</dt>
              <dd>
                {value.lastLoginAt
                  ? dateTime.format(new Date(value.lastLoginAt))
                  : "Chưa ghi nhận"}
              </dd>
            </div>
            <div>
              <dt>Phiên đang hoạt động</dt>
              <dd>{value.activeSessions}</dd>
            </div>
            <div>
              <dt>Lý do trạng thái</dt>
              <dd>{value.statusReason || "Không có"}</dd>
            </div>
          </dl>
        </article>

        <article className="admin-panel">
          <div className="panel-title">
            <div>
              <span className="eyebrow">HÀNH ĐỘNG BẢO MẬT</span>
              <h2>Quản trị tài khoản</h2>
            </div>
          </div>
          {isSelf && (
            <div className="admin-alert warning">
              Bạn không thể tự khóa, tự hạ quyền hoặc thu hồi phiên quản trị tại
              đây.
            </div>
          )}
          <div className="admin-action-list">
            {value.status === "ACTIVE" ? (
              <button
                className="danger-outline"
                disabled={isSelf}
                onClick={() => openAction("SUSPEND")}
              >
                <LockKeyhole /> Tạm ngừng tài khoản
              </button>
            ) : (
              <button
                className="success-outline"
                disabled={isSelf}
                onClick={() => openAction("ACTIVATE")}
              >
                <RefreshCcw /> Kích hoạt tài khoản
              </button>
            )}
            {value.role === "USER" ? (
              <button
                className="ghost"
                disabled={isSelf || value.status !== "ACTIVE"}
                onClick={() => openAction("PROMOTE")}
              >
                <ShieldCheck /> Cấp quyền Admin
              </button>
            ) : (
              <button
                className="ghost"
                disabled={isSelf}
                onClick={() => openAction("DEMOTE")}
              >
                <ShieldOff /> Hạ xuống USER
              </button>
            )}
            <button
              className="ghost"
              disabled={isSelf || value.activeSessions === 0}
              onClick={() => openAction("REVOKE")}
            >
              <KeyRound /> Thu hồi toàn bộ phiên
            </button>
          </div>
        </article>
      </div>

      <article className="admin-panel session-panel">
        <div className="panel-title">
          <div>
            <span className="eyebrow">SESSION METADATA</span>
            <h2>Phiên đăng nhập</h2>
          </div>
          <span>
            {sessions.data?.filter((item) => item.active).length ?? 0} hoạt động
          </span>
        </div>
        <div className="session-list">
          {sessions.data?.map((session) => (
            <div key={session.id}>
              <Monitor />
              <span>
                <strong>
                  {session.deviceName || "Thiết bị không xác định"}
                </strong>
                <small>
                  {session.ipAddress || "Không có IP"} · Tạo{" "}
                  {dateTime.format(new Date(session.createdAt))}
                </small>
                <small className="session-agent">
                  {session.userAgent || "Không có thông tin trình duyệt"}
                </small>
              </span>
              <span
                className={`session-state ${session.active ? "active" : ""}`}
              >
                {session.active ? "Đang hoạt động" : "Đã kết thúc"}
              </span>
              {session.active && !isSelf && (
                <button
                  className="danger-outline compact"
                  onClick={() => openAction("REVOKE_SESSION", session.id)}
                >
                  <KeyRound size={14} /> Thu hồi
                </button>
              )}
            </div>
          ))}
          {!sessions.isLoading && sessions.data?.length === 0 && (
            <div className="admin-empty">
              <UserCog />
              <span>Chưa ghi nhận phiên đăng nhập.</span>
            </div>
          )}
        </div>
      </article>

      {action && (
        <div className="modal-backdrop" role="presentation">
          <section
            className="admin-action-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="sensitive-action-title"
          >
            <button
              className="icon-btn modal-close"
              onClick={() => setAction(null)}
              aria-label="Đóng xác thực quản trị"
            >
              <X />
            </button>
            <span className="modal-security-icon">
              <ShieldCheck />
            </span>
            <h2 id="sensitive-action-title">{actionTitle(action)}</h2>
            <p>
              Nhập lý do và mật khẩu Admin. Phiên xác thực chỉ có hiệu lực trong
              5 phút.
            </p>
            <form onSubmit={submitAction}>
              <label>
                Lý do thực hiện
                <textarea
                  required
                  minLength={5}
                  maxLength={500}
                  value={reason}
                  onChange={(event) => setReason(event.target.value)}
                  placeholder="Mô tả lý do quản trị..."
                />
              </label>
              <label>
                Mật khẩu Admin hiện tại
                <input
                  required
                  type="password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  autoComplete="current-password"
                />
              </label>
              {actionError && <div className="form-error">{actionError}</div>}
              <div className="modal-actions">
                <button
                  type="button"
                  className="ghost"
                  onClick={() => setAction(null)}
                >
                  Hủy
                </button>
                <button className="primary" disabled={saving}>
                  {saving ? "Đang xác thực..." : "Xác nhận thao tác"}
                </button>
              </div>
            </form>
          </section>
        </div>
      )}
    </section>
  );
}

function actionTitle(action: SessionSensitiveAction) {
  return {
    SUSPEND: "Tạm ngừng tài khoản",
    ACTIVATE: "Kích hoạt lại tài khoản",
    PROMOTE: "Cấp quyền Administrator",
    DEMOTE: "Hạ quyền xuống User",
    REVOKE: "Thu hồi toàn bộ phiên",
    REVOKE_SESSION: "Thu hồi phiên đăng nhập",
  }[action];
}
