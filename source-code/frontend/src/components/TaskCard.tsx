import { CalendarClock, CheckCircle2, Clock3 } from "lucide-react";
import { format, formatDistanceToNowStrict, isPast } from "date-fns";
import { vi } from "date-fns/locale";
import type { Task } from "../types";
export const statusLabel = {
  TODO: "Cần làm",
  IN_PROGRESS: "Đang thực hiện",
  COMPLETED: "Hoàn thành",
  CANCELLED: "Đã hủy",
};

const formatDuration = (minutes: number) => {
  if (minutes < 60) return `${minutes} phút`;
  if (minutes >= 480 && minutes % 480 === 0)
    return `${minutes / 480} ngày làm việc`;
  const hours = Math.floor(minutes / 60);
  const remainingMinutes = minutes % 60;
  return remainingMinutes
    ? `${hours} giờ ${remainingMinutes} phút`
    : `${hours} giờ`;
};
export default function TaskCard({
  task,
  onClick,
  compact = false,
}: {
  task: Task;
  onClick?: () => void;
  compact?: boolean;
}) {
  const progress =
    task.status === "COMPLETED"
      ? 100
      : task.subtasks.length
        ? Math.round(
            (task.subtasks.filter((s) => s.completed).length * 100) /
              task.subtasks.length,
          )
        : 0;
  const overdue =
    task.dueAt &&
    isPast(new Date(task.dueAt)) &&
    !["COMPLETED", "CANCELLED"].includes(task.status);
  return (
    <article
      className={`task-card ${compact ? "compact" : ""}`}
      onClick={onClick}
      role={onClick ? "button" : undefined}
      tabIndex={onClick ? 0 : undefined}
      aria-label={onClick ? `Mở công việc ${task.title}` : undefined}
      onKeyDown={(event) => {
        if (onClick && (event.key === "Enter" || event.key === " ")) {
          event.preventDefault();
          onClick();
        }
      }}
    >
      <div className="task-card-top">
        <span className={`priority ${task.priority.toLowerCase()}`}>
          {task.priority}
        </span>
        {task.status === "COMPLETED" && (
          <CheckCircle2 size={18} className="success" />
        )}
      </div>
      <h3>{task.title}</h3>
      {!compact && task.description && <p>{task.description}</p>}
      <div className="task-meta">
        {task.dueAt && (
          <span className={overdue ? "overdue" : ""}>
            <CalendarClock size={15} />
            {overdue ? "Quá hạn " : ""}
            {format(new Date(task.dueAt), "dd/MM, HH:mm")}
          </span>
        )}
        {task.estimatedMinutes && (
          <span>
            <Clock3 size={15} />
            {formatDuration(task.estimatedMinutes)}
          </span>
        )}
      </div>
      {task.subtasks.length > 0 && (
        <div className="task-progress">
          <div>
            <i style={{ width: `${progress}%` }} />
          </div>
          <span>{progress}%</span>
        </div>
      )}
      {task.tags.length > 0 && (
        <div className="chips">
          {task.tags.map((t) => (
            <span key={t.id} style={{ borderColor: t.color, color: t.color }}>
              {t.name}
            </span>
          ))}
        </div>
      )}
    </article>
  );
}
