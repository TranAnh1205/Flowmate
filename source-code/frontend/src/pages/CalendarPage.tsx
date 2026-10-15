import { useMemo, useState } from "react";
import {
  addMonths,
  endOfMonth,
  endOfWeek,
  format,
  isSameDay,
  isSameMonth,
  startOfMonth,
  startOfWeek,
  subMonths,
  eachDayOfInterval,
} from "date-fns";
import { vi } from "date-fns/locale";
import { ChevronLeft, ChevronRight, Plus } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { api } from "../api";
import type { Task, TaskPage } from "../types";
import TaskForm from "../components/TaskForm";
export default function CalendarPage() {
  const [month, setMonth] = useState(new Date());
  const [open, setOpen] = useState(false);
  const [edit, setEdit] = useState<Task>();
  const { data = [] } = useQuery({
    queryKey: ["tasks", "calendar", format(month, "yyyy-MM")],
    queryFn: async () => {
      const from = startOfWeek(startOfMonth(month), {
        weekStartsOn: 1,
      }).toISOString();
      const to = endOfWeek(endOfMonth(month), {
        weekStartsOn: 1,
      }).toISOString();
      const r = await api.get<TaskPage>("/tasks", {
        params: { from, to, size: 100 },
      });
      return r.data.content;
    },
  });
  const days = useMemo(
    () =>
      eachDayOfInterval({
        start: startOfWeek(startOfMonth(month), { weekStartsOn: 1 }),
        end: endOfWeek(endOfMonth(month), { weekStartsOn: 1 }),
      }),
    [month],
  );
  const monthTasks = useMemo(
    () =>
      data
        .filter(
          (task) => task.dueAt && isSameMonth(new Date(task.dueAt), month),
        )
        .sort(
          (left, right) =>
            new Date(left.dueAt!).getTime() - new Date(right.dueAt!).getTime(),
        ),
    [data, month],
  );
  return (
    <>
      <div className="page-heading">
        <div>
          <span className="eyebrow">THỜI GIAN CỦA BẠN</span>
          <h1>Lịch công việc</h1>
          <p>Nhìn toàn cảnh để mỗi deadline đều có một chỗ hợp lý.</p>
        </div>
        <button
          className="primary"
          onClick={() => {
            setEdit(undefined);
            setOpen(true);
          }}
        >
          <Plus size={18} />
          Tạo công việc
        </button>
      </div>
      <section className="panel calendar-panel">
        <header className="calendar-toolbar">
          <button
            className="icon-btn"
            onClick={() => setMonth(subMonths(month, 1))}
            aria-label="Tháng trước"
          >
            <ChevronLeft />
          </button>
          <h2>{format(month, "'Tháng' M, yyyy", { locale: vi })}</h2>
          <button
            className="icon-btn"
            onClick={() => setMonth(addMonths(month, 1))}
            aria-label="Tháng sau"
          >
            <ChevronRight />
          </button>
          <button className="ghost" onClick={() => setMonth(new Date())}>
            Hôm nay
          </button>
        </header>
        <div className="weekdays">
          {["T2", "T3", "T4", "T5", "T6", "T7", "CN"].map((x) => (
            <span key={x}>{x}</span>
          ))}
        </div>
        <div className="calendar-grid">
          {days.map((day) => {
            const items = data.filter(
              (t) => t.dueAt && isSameDay(new Date(t.dueAt), day),
            );
            return (
              <div
                key={day.toISOString()}
                className={`calendar-day ${!isSameMonth(day, month) ? "muted" : ""} ${isSameDay(day, new Date()) ? "today" : ""}`}
              >
                <b>{format(day, "d")}</b>
                <div>
                  {items.slice(0, 3).map((t) => (
                    <button
                      key={t.id}
                      className={`cal-task ${t.priority.toLowerCase()}`}
                      onClick={() => {
                        setEdit(t);
                        setOpen(true);
                      }}
                      title={t.title}
                    >
                      {t.title}
                    </button>
                  ))}
                  {items.length > 3 && (
                    <small>+{items.length - 3} việc khác</small>
                  )}
                </div>
              </div>
            );
          })}
        </div>
        <div className="calendar-agenda">
          <h3>Công việc trong tháng</h3>
          {monthTasks.length ? (
            <div className="calendar-agenda-list">
              {monthTasks.map((task) => (
                <button
                  key={task.id}
                  onClick={() => {
                    setEdit(task);
                    setOpen(true);
                  }}
                >
                  <time dateTime={task.dueAt}>
                    {format(new Date(task.dueAt!), "EEEE, dd/MM • HH:mm", {
                      locale: vi,
                    })}
                  </time>
                  <strong>{task.title}</strong>
                  <span className={`priority ${task.priority.toLowerCase()}`}>
                    {task.priority}
                  </span>
                </button>
              ))}
            </div>
          ) : (
            <div className="calendar-agenda-empty">
              Chưa có công việc nào có thời hạn trong tháng này.
            </div>
          )}
        </div>
      </section>
      <TaskForm
        open={open}
        task={edit}
        onClose={() => {
          setOpen(false);
          setEdit(undefined);
        }}
      />
    </>
  );
}
