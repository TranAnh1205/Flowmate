import { useQuery } from "@tanstack/react-query";
import {
  Activity,
  Bot,
  CheckCircle2,
  Database,
  RefreshCw,
  Server,
  ShieldCheck,
  XCircle,
} from "lucide-react";
import {
  getAdminAiStatus,
  getAdminNotificationStatistics,
  getAdminSystemHealth,
} from "../../admin/admin-api";
import { getApiErrorMessage } from "../../api";

const dateTime = new Intl.DateTimeFormat("vi-VN", {
  dateStyle: "short",
  timeStyle: "medium",
});

export default function AdminSystemPage() {
  const health = useQuery({
    queryKey: ["admin", "system", "health"],
    queryFn: getAdminSystemHealth,
    refetchInterval: 30000,
  });
  const ai = useQuery({
    queryKey: ["admin", "ai", "status"],
    queryFn: getAdminAiStatus,
    refetchInterval: 60000,
  });
  const notifications = useQuery({
    queryKey: ["admin", "statistics", "notifications"],
    queryFn: getAdminNotificationStatistics,
    refetchInterval: 30000,
  });

  const refresh = () => {
    void health.refetch();
    void ai.refetch();
    void notifications.refetch();
  };

  return (
    <section>
      <div className="admin-page-heading">
        <div>
          <span className="eyebrow">VẬN HÀNH AN TOÀN</span>
          <h1>Trạng thái hệ thống</h1>
          <p>Chỉ hiển thị health và metadata đã làm sạch, không lộ secret.</p>
        </div>
        <button className="ghost" onClick={refresh}>
          <RefreshCw size={17} /> Kiểm tra lại
        </button>
      </div>

      {health.error && (
        <div className="admin-alert error">
          {getApiErrorMessage(
            health.error,
            "Không thể kiểm tra trạng thái service.",
          )}
        </div>
      )}

      <div className="system-service-grid">
        {(
          [
            ["Auth Service", health.data?.authService, ShieldCheck],
            ["Task Service", health.data?.taskService, Database],
            ["AI Service", health.data?.aiService, Bot],
            [
              "Notification Service",
              health.data?.notificationService,
              Activity,
            ],
          ] as const
        ).map(([label, value, Icon]) => (
          <article className="service-health-card" key={label}>
            <span
              className={`service-health-icon ${value?.status === "UP" ? "up" : "down"}`}
            >
              <Icon />
            </span>
            <div>
              <span>{label}</span>
              <strong>{value?.status || "Đang kiểm tra"}</strong>
              <small>
                {value
                  ? `${value.responseTimeMs} ms · ${dateTime.format(new Date(value.checkedAt))}`
                  : "Chưa có dữ liệu"}
              </small>
            </div>
            {value?.status === "UP" ? (
              <CheckCircle2 className="health-result up" />
            ) : (
              <XCircle className="health-result down" />
            )}
          </article>
        ))}
      </div>

      <div className="admin-panels system-panels">
        <article className="admin-panel">
          <div className="panel-title">
            <div>
              <span className="eyebrow">AI STATUS</span>
              <h2>Cấu hình AI đã làm sạch</h2>
            </div>
            <Bot />
          </div>
          <dl className="admin-detail-list">
            <div>
              <dt>Chế độ</dt>
              <dd>{ai.data?.mode || "Đang tải"}</dd>
            </div>
            <div>
              <dt>Model</dt>
              <dd>{ai.data?.model || "—"}</dd>
            </div>
            <div>
              <dt>Provider</dt>
              <dd>{ai.data?.provider || "—"}</dd>
            </div>
            <div>
              <dt>API key</dt>
              <dd className="protected-value">Không được hiển thị</dd>
            </div>
          </dl>
        </article>

        <article className="admin-panel">
          <div className="panel-title">
            <div>
              <span className="eyebrow">REMINDER QUEUE</span>
              <h2>Vận hành thông báo</h2>
            </div>
            <Server />
          </div>
          <dl className="admin-detail-list">
            <div>
              <dt>Đang chờ</dt>
              <dd>{notifications.data?.pendingReminders ?? "—"}</dd>
            </div>
            <div>
              <dt>Đang xử lý</dt>
              <dd>{notifications.data?.processingReminders ?? "—"}</dd>
            </div>
            <div>
              <dt>Đã xử lý</dt>
              <dd>{notifications.data?.processedReminders ?? "—"}</dd>
            </div>
            <div>
              <dt>Đến hạn chưa xử lý</dt>
              <dd>{notifications.data?.overduePendingReminders ?? "—"}</dd>
            </div>
          </dl>
        </article>
      </div>
    </section>
  );
}
