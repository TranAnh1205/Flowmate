import { Bell, CheckCheck, Clock3 } from "lucide-react";
import { formatDistanceToNow } from "date-fns";
import { vi } from "date-fns/locale";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, getApiErrorMessage } from "../api";
import type { NotificationItem } from "../types";
export default function NotificationsPage() {
  const q = useQueryClient();
  const {
    data = [],
    isLoading,
    error: loadError,
  } = useQuery({
    queryKey: ["notifications"],
    queryFn: async () => {
      const r = await api.get("/notifications", { params: { size: 50 } });
      return r.data.content as NotificationItem[];
    },
  });
  const read = useMutation({
    mutationFn: (id: string) => api.patch(`/notifications/${id}/read`),
    onSuccess: () => {
      q.invalidateQueries({ queryKey: ["notifications"] });
      q.invalidateQueries({ queryKey: ["unread"] });
    },
  });
  const all = useMutation({
    mutationFn: () => api.patch("/notifications/read-all"),
    onSuccess: () => {
      q.invalidateQueries({ queryKey: ["notifications"] });
      q.invalidateQueries({ queryKey: ["unread"] });
    },
  });
  return (
    <>
      <div className="page-heading">
        <div>
          <span className="eyebrow">TRUNG TÂM NHẮC VIỆC</span>
          <h1>Thông báo</h1>
          <p>Không bỏ lỡ những thời điểm quan trọng trong lịch cá nhân.</p>
        </div>
        <button
          className="ghost"
          onClick={() => all.mutate()}
          disabled={!data.some((item) => !item.read) || all.isPending}
        >
          <CheckCheck size={18} />
          Đánh dấu tất cả đã đọc
        </button>
      </div>
      {(loadError || read.error || all.error) && (
        <div className="form-error" role="alert">
          {getApiErrorMessage(
            loadError || read.error || all.error,
            "Không thể cập nhật thông báo.",
          )}
        </div>
      )}
      <section className="panel notification-list">
        {isLoading ? (
          <div className="empty-state">
            <Clock3 />
            <h3>Đang tải thông báo...</h3>
          </div>
        ) : data.length ? (
          data.map((n) => (
            <button
              key={n.id}
              className={n.read ? "read" : ""}
              onClick={() => !n.read && read.mutate(n.id)}
              disabled={n.read || read.isPending}
            >
              <span className="notification-icon">
                <Bell />
              </span>
              <span>
                <strong>{n.title}</strong>
                <p>{n.message}</p>
                <small>
                  <Clock3 size={13} />
                  {formatDistanceToNow(new Date(n.createdAt), {
                    addSuffix: true,
                    locale: vi,
                  })}
                </small>
              </span>
              {!n.read && <i />}
            </button>
          ))
        ) : (
          <div className="empty-state">
            <Bell />
            <h3>Chưa có thông báo</h3>
            <p>Các lời nhắc và cập nhật quan trọng sẽ xuất hiện tại đây.</p>
          </div>
        )}
      </section>
    </>
  );
}
