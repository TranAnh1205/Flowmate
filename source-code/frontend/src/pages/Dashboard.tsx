import {
  ArrowRight,
  CalendarCheck2,
  CheckCircle2,
  Clock3,
  Flame,
  Plus,
  Sparkles,
  TriangleAlert,
} from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { api } from "../api";
import { useAuth } from "../auth";
import type { DashboardStats, Task } from "../types";
import TaskCard from "../components/TaskCard";
import { useNavigate } from "react-router-dom";
export default function Dashboard() {
  const { user } = useAuth();
  const nav = useNavigate();
  const { data: stats } = useQuery({
    queryKey: ["stats", user?.timezone],
    queryFn: async () => {
      const r = await api.get<DashboardStats>("/statistics/dashboard", {
        params: { timezone: user?.timezone || "Asia/Bangkok" },
      });
      return r.data;
    },
  });
  const { data: today = [] } = useQuery({
    queryKey: ["tasks", "today", user?.timezone],
    queryFn: async () => {
      const r = await api.get<Task[]>("/tasks/today", {
        params: { timezone: user?.timezone || "Asia/Bangkok" },
      });
      return r.data;
    },
  });
  const s = stats || {
    totalTasks: 0,
    todo: 0,
    inProgress: 0,
    completed: 0,
    cancelled: 0,
    overdue: 0,
    dueToday: 0,
    weeklyCompletionRate: 0,
  };
  return (
    <>
      <div className="page-heading hero-heading">
        <div>
          <span className="eyebrow">TỔNG QUAN HÔM NAY</span>
          <h1>
            Chào {user?.fullName?.split(" ").slice(-1)[0]}, hôm nay mình làm gì?
          </h1>
          <p>Giữ nhịp vừa đủ. Hoàn thành điều quan trọng nhất trước.</p>
        </div>
        <button className="primary" onClick={() => nav("/app/ai-assistant")}>
          <Sparkles size={18} />
          Lập kế hoạch với AI
        </button>
      </div>
      <section className="stat-grid">
        <div className="stat-card accent">
          <span>
            <CalendarCheck2 />
          </span>
          <div>
            <small>Việc hôm nay</small>
            <strong>{s.dueToday}</strong>
            <em>{s.inProgress} đang thực hiện</em>
          </div>
        </div>
        <div className="stat-card">
          <span className="green">
            <CheckCircle2 />
          </span>
          <div>
            <small>Đã hoàn thành</small>
            <strong>{s.completed}</strong>
            <em>{s.weeklyCompletionRate}% tỷ lệ chung</em>
          </div>
        </div>
        <div className="stat-card">
          <span className="orange">
            <Clock3 />
          </span>
          <div>
            <small>Đang thực hiện</small>
            <strong>{s.inProgress}</strong>
            <em>{s.todo} việc đang chờ</em>
          </div>
        </div>
        <div className="stat-card">
          <span className="red">
            <TriangleAlert />
          </span>
          <div>
            <small>Đã quá hạn</small>
            <strong>{s.overdue}</strong>
            <em>{s.overdue ? "Cần xử lý sớm" : "Không có việc trễ"}</em>
          </div>
        </div>
      </section>
      <div className="dashboard-grid">
        <section className="panel focus-panel">
          <div className="panel-heading">
            <div>
              <span className="eyebrow">ƯU TIÊN</span>
              <h2>Việc cần làm hôm nay</h2>
            </div>
            <button
              className="text-btn"
              onClick={() => nav("/app/tasks/today")}
            >
              Xem tất cả <ArrowRight size={16} />
            </button>
          </div>
          {today.length ? (
            <div className="task-list">
              {today.slice(0, 4).map((t) => (
                <TaskCard
                  key={t.id}
                  task={t}
                  onClick={() =>
                    nav(`/app/tasks?q=${encodeURIComponent(t.title)}`)
                  }
                />
              ))}
            </div>
          ) : (
            <div className="empty-state">
              <CheckCircle2 />
              <h3>Hôm nay đang rất thoáng</h3>
              <p>Tạo một công việc mới hoặc nhờ AI lập kế hoạch.</p>
              <button
                className="ghost"
                onClick={() => nav("/app/tasks?new=true")}
              >
                <Plus size={17} />
                Tạo công việc
              </button>
            </div>
          )}
        </section>
        <aside className="panel ai-insight">
          <div className="ai-orb">
            <Sparkles />
          </div>
          <span className="eyebrow light">GỢI Ý TỪ FLOWMATE AI</span>
          <h2>
            {s.overdue
              ? "Bắt đầu từ việc quá hạn quan trọng nhất."
              : "Bạn đang kiểm soát tốt lịch làm việc."}
          </h2>
          <p>
            {s.overdue
              ? `Có ${s.overdue} công việc cần được sắp xếp lại. Tôi có thể giúp chia nhỏ và lập lịch.`
              : "Hãy chọn một việc quan trọng, dành cho nó một khung tập trung và bắt đầu."}
          </p>
          <button onClick={() => nav("/app/ai-assistant")}>
            Mở trợ lý AI <ArrowRight size={17} />
          </button>
          <div className="insight-streak">
            <Flame />
            <span>
              <strong>{s.completed} nhiệm vụ</strong>
              <small>đã hoàn thành</small>
            </span>
          </div>
        </aside>
      </div>
    </>
  );
}
