import { useQuery } from "@tanstack/react-query";
import {
  Activity,
  Bell,
  CheckCircle2,
  Clock3,
  ListChecks,
  ShieldAlert,
  UserCheck,
  Users,
} from "lucide-react";
import {
  getAdminNotificationStatistics,
  getAdminSystemHealth,
  getAdminTaskStatistics,
  getAdminUserStatistics,
} from "../../admin/admin-api";
import { getApiErrorMessage } from "../../api";

const number = new Intl.NumberFormat("vi-VN");

export default function AdminDashboard() {
  const users = useQuery({
    queryKey: ["admin", "statistics", "users"],
    queryFn: getAdminUserStatistics,
  });
  const tasks = useQuery({
    queryKey: ["admin", "statistics", "tasks"],
    queryFn: getAdminTaskStatistics,
  });
  const notifications = useQuery({
    queryKey: ["admin", "statistics", "notifications"],
    queryFn: getAdminNotificationStatistics,
  });
  const health = useQuery({
    queryKey: ["admin", "system", "health"],
    queryFn: getAdminSystemHealth,
    refetchInterval: 30000,
  });

  const firstError =
    users.error || tasks.error || notifications.error || health.error;

  return (
    <section>
      <div className="admin-page-heading">
        <div>
          <span className="eyebrow">TỔNG QUAN HỆ THỐNG</span>
          <h1>Dashboard quản trị</h1>
          <p>Theo dõi tài khoản và vận hành bằng dữ liệu tổng hợp.</p>
        </div>
        <span
          className={`system-summary ${health.data?.status === "UP" ? "success" : "warning"}`}
        >
          <Activity size={17} />
          {health.isLoading
            ? "Đang kiểm tra..."
            : health.data?.status === "UP"
              ? "Tất cả service hoạt động"
              : "Hệ thống cần kiểm tra"}
        </span>
      </div>

      {firstError && (
        <div className="admin-alert error">
          {getApiErrorMessage(firstError, "Không thể tải toàn bộ thống kê.")}
        </div>
      )}

      <div className="admin-stat-grid">
        <StatCard
          icon={<Users />}
          label="Tổng người dùng"
          value={users.data?.totalUsers}
          note={`${users.data?.newUsersThisWeek ?? 0} tài khoản mới tuần này`}
          tone="blue"
        />
        <StatCard
          icon={<UserCheck />}
          label="Đang hoạt động"
          value={users.data?.activeUsers}
          note={`${users.data?.adminUsers ?? 0} quản trị viên`}
          tone="green"
        />
        <StatCard
          icon={<ShieldAlert />}
          label="Đã tạm ngừng"
          value={
            (users.data?.suspendedUsers ?? 0) + (users.data?.lockedUsers ?? 0)
          }
          note="Không thể đăng nhập hoặc refresh"
          tone="orange"
        />
        <StatCard
          icon={<ListChecks />}
          label="Tổng công việc"
          value={tasks.data?.totalTasks}
          note={`${tasks.data?.createdThisWeek ?? 0} công việc mới tuần này`}
          tone="purple"
        />
        <StatCard
          icon={<CheckCircle2 />}
          label="Đã hoàn thành"
          value={tasks.data?.completed}
          note={`${tasks.data?.completionRate ?? 0}% tỷ lệ hoàn thành`}
          tone="green"
        />
        <StatCard
          icon={<Clock3 />}
          label="Công việc quá hạn"
          value={tasks.data?.overdue}
          note="Chỉ thống kê, không hiển thị nội dung"
          tone="red"
        />
        <StatCard
          icon={<Bell />}
          label="Thông báo"
          value={notifications.data?.totalNotifications}
          note={`${notifications.data?.unreadNotifications ?? 0} chưa đọc`}
          tone="blue"
        />
        <StatCard
          icon={<Clock3 />}
          label="Reminder đang chờ"
          value={notifications.data?.pendingReminders}
          note={`${notifications.data?.overduePendingReminders ?? 0} đã đến hạn`}
          tone="orange"
        />
      </div>

      <div className="admin-panels">
        <article className="admin-panel">
          <div className="panel-title">
            <div>
              <span className="eyebrow">PHÂN BỔ CÔNG VIỆC</span>
              <h2>Trạng thái toàn hệ thống</h2>
            </div>
          </div>
          <div className="task-state-bars">
            <StateBar
              label="Cần làm"
              value={tasks.data?.todo ?? 0}
              total={tasks.data?.totalTasks ?? 0}
              color="#4f6df5"
            />
            <StateBar
              label="Đang thực hiện"
              value={tasks.data?.inProgress ?? 0}
              total={tasks.data?.totalTasks ?? 0}
              color="#8164ed"
            />
            <StateBar
              label="Hoàn thành"
              value={tasks.data?.completed ?? 0}
              total={tasks.data?.totalTasks ?? 0}
              color="#20a875"
            />
            <StateBar
              label="Đã hủy"
              value={tasks.data?.cancelled ?? 0}
              total={tasks.data?.totalTasks ?? 0}
              color="#8892a5"
            />
          </div>
        </article>

        <article className="admin-panel">
          <div className="panel-title">
            <div>
              <span className="eyebrow">SERVICE HEALTH</span>
              <h2>Trạng thái microservice</h2>
            </div>
          </div>
          <div className="service-list">
            {(
              [
                ["Auth Service", health.data?.authService],
                ["Task Service", health.data?.taskService],
                ["AI Service", health.data?.aiService],
                ["Notification Service", health.data?.notificationService],
              ] as const
            ).map(([label, value]) => (
              <div key={label}>
                <span
                  className={`health-dot ${value?.status === "UP" ? "up" : "down"}`}
                />
                <strong>{label}</strong>
                <small>
                  {value
                    ? `${value.status} · ${value.responseTimeMs} ms`
                    : "Đang kiểm tra"}
                </small>
              </div>
            ))}
          </div>
        </article>
      </div>
    </section>
  );
}

function StatCard({
  icon,
  label,
  value,
  note,
  tone,
}: {
  icon: React.ReactNode;
  label: string;
  value?: number;
  note: string;
  tone: string;
}) {
  return (
    <article className={`admin-stat-card ${tone}`}>
      <span className="stat-icon">{icon}</span>
      <div>
        <span>{label}</span>
        <strong>{value === undefined ? "—" : number.format(value)}</strong>
        <small>{note}</small>
      </div>
    </article>
  );
}

function StateBar({
  label,
  value,
  total,
  color,
}: {
  label: string;
  value: number;
  total: number;
  color: string;
}) {
  const percentage = total ? Math.min(100, (value / total) * 100) : 0;
  return (
    <div>
      <span>
        <strong>{label}</strong>
        <small>{number.format(value)}</small>
      </span>
      <div className="state-track">
        <i style={{ width: `${percentage}%`, background: color }} />
      </div>
    </div>
  );
}
