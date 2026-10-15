import { useState } from "react";
import {
  DndContext,
  KeyboardSensor,
  PointerSensor,
  useDraggable,
  useDroppable,
  useSensor,
  useSensors,
  type DragEndEvent,
} from "@dnd-kit/core";
import { CSS } from "@dnd-kit/utilities";
import { GripVertical, Plus } from "lucide-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, getApiErrorMessage } from "../api";
import type { Status, Task, TaskPage } from "../types";
import TaskCard from "../components/TaskCard";
import TaskForm from "../components/TaskForm";

const columns: [Status, string][] = [
  ["TODO", "Cần làm"],
  ["IN_PROGRESS", "Đang thực hiện"],
  ["COMPLETED", "Hoàn thành"],
];

function Draggable({
  task,
  onEdit,
}: {
  task: Task;
  onEdit: (task: Task) => void;
}) {
  const { attributes, listeners, setNodeRef, transform, isDragging } =
    useDraggable({
      id: task.id,
      data: { task },
    });
  return (
    <div
      ref={setNodeRef}
      className="draggable-task"
      style={{
        transform: CSS.Translate.toString(transform),
        opacity: isDragging ? 0.45 : 1,
      }}
    >
      <button
        className="drag-handle"
        aria-label={`Kéo ${task.title}`}
        {...listeners}
        {...attributes}
      >
        <GripVertical size={16} />
      </button>
      <TaskCard task={task} compact onClick={() => onEdit(task)} />
    </div>
  );
}

function Column({
  status,
  title,
  tasks,
  onEdit,
}: {
  status: Status;
  title: string;
  tasks: Task[];
  onEdit: (task: Task) => void;
}) {
  const { setNodeRef, isOver } = useDroppable({ id: status });
  return (
    <section
      ref={setNodeRef}
      className={`board-column ${isOver ? "over" : ""}`}
    >
      <header>
        <span className={`column-dot ${status.toLowerCase()}`} />
        <h2>{title}</h2>
        <b>{tasks.length}</b>
      </header>
      <div className="board-stack">
        {tasks.map((task) => (
          <Draggable key={task.id} task={task} onEdit={onEdit} />
        ))}
        {!tasks.length && (
          <div className="column-empty">Kéo công việc vào đây</div>
        )}
      </div>
    </section>
  );
}

export default function BoardPage() {
  const [open, setOpen] = useState(false);
  const [edit, setEdit] = useState<Task>();
  const [error, setError] = useState("");
  const [feedback, setFeedback] = useState("");
  const queryClient = useQueryClient();
  const sensors = useSensors(
    useSensor(PointerSensor, {
      activationConstraint: { distance: 8 },
    }),
    useSensor(KeyboardSensor),
  );
  const queryKey = ["tasks", "board"] as const;
  const {
    data = [],
    isLoading,
    isError,
    error: loadError,
    refetch,
  } = useQuery({
    queryKey,
    queryFn: async () => {
      const response = await api.get<TaskPage>("/tasks", {
        params: { size: 100, sort: "dueAt,asc" },
      });
      return response.data.content;
    },
  });

  const move = useMutation({
    mutationFn: ({ id, status }: { id: string; status: Status }) =>
      api.patch(`/tasks/${id}/status`, { status }),
    onMutate: async ({ id, status }) => {
      setError("");
      setFeedback("");
      await queryClient.cancelQueries({ queryKey });
      const previous = queryClient.getQueryData<Task[]>(queryKey) ?? [];
      queryClient.setQueryData<Task[]>(
        queryKey,
        previous.map((task) => (task.id === id ? { ...task, status } : task)),
      );
      return { previous };
    },
    onError: (_error, _variables, context) => {
      if (context?.previous)
        queryClient.setQueryData(queryKey, context.previous);
      setError(
        "Không thể chuyển trạng thái. Bảng đã được khôi phục về dữ liệu trước đó.",
      );
    },
    onSuccess: (_response, variables) => {
      const label = columns.find(
        ([status]) => status === variables.status,
      )?.[1];
      setFeedback(`Đã chuyển công việc sang “${label}”.`);
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ["tasks"] });
      queryClient.invalidateQueries({ queryKey: ["stats"] });
    },
  });

  const handleDragEnd = (event: DragEndEvent) => {
    if (!event.over) return;
    const task = event.active.data.current?.task as Task;
    const status = event.over.id as Status;
    if (task.status !== status) move.mutate({ id: task.id, status });
  };

  return (
    <>
      <div className="page-heading">
        <div>
          <span className="eyebrow">LUỒNG CÔNG VIỆC</span>
          <h1>Bảng Kanban</h1>
          <p>Kéo thả để cập nhật tiến độ ngay khi bạn chuyển nhịp.</p>
        </div>
        <button
          className="primary"
          onClick={() => {
            setEdit(undefined);
            setOpen(true);
          }}
        >
          <Plus size={18} />
          Thêm công việc
        </button>
      </div>
      {error && (
        <div className="form-error board-error" role="alert">
          {error}
        </div>
      )}
      {feedback && (
        <div className="success-message board-feedback" role="status">
          {feedback}
        </div>
      )}
      {isLoading ? (
        <div className="panel loading-list" role="status">
          Đang tải bảng Kanban...
        </div>
      ) : isError ? (
        <div className="form-error board-load-error" role="alert">
          <span>
            {getApiErrorMessage(loadError, "Không thể tải bảng Kanban.")}
          </span>
          <button className="ghost small" onClick={() => refetch()}>
            Thử lại
          </button>
        </div>
      ) : (
        <DndContext sensors={sensors} onDragEnd={handleDragEnd}>
          <div className="board">
            {columns.map(([status, title]) => (
              <Column
                key={status}
                status={status}
                title={title}
                tasks={data.filter((task) => task.status === status)}
                onEdit={(task) => {
                  setEdit(task);
                  setOpen(true);
                }}
              />
            ))}
          </div>
        </DndContext>
      )}
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
