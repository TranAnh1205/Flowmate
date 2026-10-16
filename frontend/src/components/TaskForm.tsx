import { useEffect, useRef, useState } from "react";
import { Pencil, Save, Trash2, X } from "lucide-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, getApiErrorMessage } from "../api";
import { showFeedback } from "../feedback";
import type { Category, Priority, Status, Tag, Task } from "../types";

type Goal = { id: string; title: string; status: string };

const emptyForm = {
  title: "",
  description: "",
  status: "TODO" as Status,
  priority: "MEDIUM" as Priority,
  categoryId: "",
  goalId: "",
  tagIds: [] as string[],
  startAt: "",
  dueAt: "",
  estimatedMinutes: 60,
  reminderMinutesBefore: 30,
  subtasksText: "",
};

function toLocalDateTime(value?: string) {
  if (!value) return "";
  const date = new Date(value);
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 16);
}

export default function TaskForm({
  open,
  onClose,
  onSaved,
  task,
}: {
  open: boolean;
  onClose: () => void;
  onSaved?: (created: boolean) => void;
  task?: Task;
}) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState(emptyForm);
  const [clientError, setClientError] = useState("");
  const [newSubtask, setNewSubtask] = useState("");
  const [subtasks, setSubtasks] = useState<Task["subtasks"]>([]);
  const [editingSubtask, setEditingSubtask] = useState<{
    id: string;
    title: string;
  }>();
  const titleInputRef = useRef<HTMLInputElement>(null);
  const previouslyFocusedRef = useRef<HTMLElement | null>(null);
  const onCloseRef = useRef(onClose);

  useEffect(() => {
    onCloseRef.current = onClose;
  }, [onClose]);

  const { data: categories = [] } = useQuery({
    queryKey: ["categories"],
    queryFn: async () => (await api.get<Category[]>("/categories")).data,
    enabled: open,
  });
  const { data: tags = [] } = useQuery({
    queryKey: ["tags"],
    queryFn: async () => (await api.get<Tag[]>("/tags")).data,
    enabled: open,
  });
  const { data: goals = [] } = useQuery({
    queryKey: ["goals"],
    queryFn: async () => (await api.get<Goal[]>("/goals")).data,
    enabled: open,
  });

  useEffect(() => {
    if (!open) return;
    setClientError("");
    setNewSubtask("");
    setSubtasks(task?.subtasks ?? []);
    setEditingSubtask(undefined);
    setForm(
      task
        ? {
            title: task.title,
            description: task.description || "",
            status: task.status,
            priority: task.priority,
            categoryId: task.categoryId || "",
            goalId: task.goalId || "",
            tagIds: task.tags.map((tag) => tag.id),
            startAt: toLocalDateTime(task.startAt),
            dueAt: toLocalDateTime(task.dueAt),
            estimatedMinutes: task.estimatedMinutes || 60,
            reminderMinutesBefore: task.reminderMinutesBefore ?? 30,
            subtasksText: "",
          }
        : emptyForm,
    );
  }, [open, task]);

  useEffect(() => {
    if (!open) return;
    previouslyFocusedRef.current = document.activeElement as HTMLElement | null;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    window.requestAnimationFrame(() => titleInputRef.current?.focus());
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") onCloseRef.current();
    };
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("keydown", handleKeyDown);
      document.body.style.overflow = previousOverflow;
      previouslyFocusedRef.current?.focus();
    };
  }, [open]);

  const save = useMutation({
    mutationFn: async () => {
      if (
        form.startAt &&
        form.dueAt &&
        new Date(form.dueAt) < new Date(form.startAt)
      ) {
        throw new Error("Deadline phải sau thời gian bắt đầu.");
      }
      const body = {
        title: form.title.trim(),
        description: form.description || null,
        status: form.status,
        priority: form.priority,
        categoryId: form.categoryId || null,
        goalId: form.goalId || null,
        tagIds: form.tagIds,
        startAt: form.startAt ? new Date(form.startAt).toISOString() : null,
        dueAt: form.dueAt ? new Date(form.dueAt).toISOString() : null,
        estimatedMinutes: form.estimatedMinutes,
        reminderMinutesBefore: form.dueAt ? form.reminderMinutesBefore : null,
        ...(!task
          ? {
              subtasks: form.subtasksText
                .split("\n")
                .map((title) => title.trim())
                .filter(Boolean)
                .map((title) => ({ title })),
            }
          : {}),
      };
      return task
        ? api.put(`/tasks/${task.id}`, body)
        : api.post("/tasks", body);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["tasks"] });
      queryClient.invalidateQueries({ queryKey: ["stats"] });
      showFeedback(task ? "Đã cập nhật công việc." : "Đã tạo công việc mới.");
      onSaved?.(!task);
      onClose();
    },
    onError: (error) =>
      setClientError(getApiErrorMessage(error, "Không thể lưu công việc.")),
  });
  const remove = useMutation({
    mutationFn: () => api.delete(`/tasks/${task?.id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["tasks"] });
      queryClient.invalidateQueries({ queryKey: ["stats"] });
      showFeedback("Đã xóa công việc.");
      onClose();
    },
    onError: (error) =>
      setClientError(getApiErrorMessage(error, "Không thể xóa công việc.")),
  });
  const refreshTasks = () =>
    queryClient.invalidateQueries({ queryKey: ["tasks"] });
  const addSubtask = useMutation({
    mutationFn: async () =>
      (
        await api.post<Task["subtasks"][number]>(
          `/tasks/${task?.id}/subtasks`,
          {
            title: newSubtask.trim(),
            position: subtasks.length,
          },
        )
      ).data,
    onSuccess: (created) => {
      setSubtasks((current) => [...current, created]);
      setNewSubtask("");
      refreshTasks();
    },
    onError: (error) =>
      setClientError(getApiErrorMessage(error, "Không thể thêm bước mới.")),
  });
  const completeSubtask = useMutation({
    mutationFn: async (id: string) =>
      (
        await api.patch<Task["subtasks"][number]>(
          `/tasks/${task?.id}/subtasks/${id}/complete`,
        )
      ).data,
    onSuccess: (updated) => {
      setSubtasks((current) =>
        current.map((item) => (item.id === updated.id ? updated : item)),
      );
      refreshTasks();
    },
    onError: (error) =>
      setClientError(
        getApiErrorMessage(error, "Không thể cập nhật checklist."),
      ),
  });
  const deleteSubtask = useMutation({
    mutationFn: (id: string) => api.delete(`/tasks/${task?.id}/subtasks/${id}`),
    onSuccess: (_response, id) => {
      setSubtasks((current) => current.filter((item) => item.id !== id));
      refreshTasks();
    },
    onError: (error) =>
      setClientError(getApiErrorMessage(error, "Không thể xóa bước này.")),
  });
  const updateSubtask = useMutation({
    mutationFn: async ({ id, title }: { id: string; title: string }) =>
      (
        await api.put<Task["subtasks"][number]>(
          `/tasks/${task?.id}/subtasks/${id}`,
          {
            title: title.trim(),
            position: subtasks.find((item) => item.id === id)?.position ?? 0,
          },
        )
      ).data,
    onSuccess: (updated) => {
      setSubtasks((current) =>
        current.map((item) => (item.id === updated.id ? updated : item)),
      );
      setEditingSubtask(undefined);
      refreshTasks();
    },
    onError: (error) =>
      setClientError(getApiErrorMessage(error, "Không thể sửa bước này.")),
  });

  if (!open) return null;
  const toggleTag = (tagId: string) =>
    setForm((current) => ({
      ...current,
      tagIds: current.tagIds.includes(tagId)
        ? current.tagIds.filter((id) => id !== tagId)
        : [...current.tagIds, tagId],
    }));

  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <form
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="task-dialog-title"
        onMouseDown={(event) => event.stopPropagation()}
        onSubmit={(event) => {
          event.preventDefault();
          save.mutate();
        }}
      >
        <div className="modal-title">
          <div>
            <span className="eyebrow">CÔNG VIỆC CÁ NHÂN</span>
            <h2 id="task-dialog-title">
              {task ? "Cập nhật công việc" : "Tạo công việc mới"}
            </h2>
          </div>
          <button
            type="button"
            className="icon-btn"
            onClick={onClose}
            aria-label="Đóng"
          >
            <X />
          </button>
        </div>
        <label>
          Tiêu đề
          <input
            ref={titleInputRef}
            required
            maxLength={255}
            value={form.title}
            onChange={(event) =>
              setForm((current) => ({ ...current, title: event.target.value }))
            }
            placeholder="Bạn muốn hoàn thành điều gì?"
          />
        </label>
        <label>
          Mô tả
          <textarea
            rows={3}
            value={form.description}
            onChange={(event) =>
              setForm((current) => ({
                ...current,
                description: event.target.value,
              }))
            }
            placeholder="Ghi chú, kết quả cần đạt..."
          />
        </label>
        <div className="form-grid">
          <label>
            Trạng thái
            <select
              value={form.status}
              onChange={(event) =>
                setForm((current) => ({
                  ...current,
                  status: event.target.value as Status,
                }))
              }
            >
              <option value="TODO">Cần làm</option>
              <option value="IN_PROGRESS">Đang thực hiện</option>
              <option value="COMPLETED">Hoàn thành</option>
              <option value="CANCELLED">Đã hủy</option>
            </select>
          </label>
          <label>
            Ưu tiên
            <select
              value={form.priority}
              onChange={(event) =>
                setForm((current) => ({
                  ...current,
                  priority: event.target.value as Priority,
                }))
              }
            >
              <option value="LOW">Thấp</option>
              <option value="MEDIUM">Trung bình</option>
              <option value="HIGH">Cao</option>
              <option value="URGENT">Khẩn cấp</option>
            </select>
          </label>
          <label>
            Danh mục
            <select
              value={form.categoryId}
              onChange={(event) =>
                setForm((current) => ({
                  ...current,
                  categoryId: event.target.value,
                }))
              }
            >
              <option value="">Không chọn</option>
              {categories.map((category) => (
                <option key={category.id} value={category.id}>
                  {category.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            Mục tiêu
            <select
              value={form.goalId}
              onChange={(event) =>
                setForm((current) => ({
                  ...current,
                  goalId: event.target.value,
                }))
              }
            >
              <option value="">Không chọn</option>
              {goals
                .filter((goal) => goal.status === "ACTIVE")
                .map((goal) => (
                  <option key={goal.id} value={goal.id}>
                    {goal.title}
                  </option>
                ))}
            </select>
          </label>
          <label>
            Bắt đầu
            <input
              type="datetime-local"
              value={form.startAt}
              onChange={(event) =>
                setForm((current) => ({
                  ...current,
                  startAt: event.target.value,
                }))
              }
            />
          </label>
          <label>
            Thời hạn
            <input
              type="datetime-local"
              value={form.dueAt}
              onChange={(event) =>
                setForm((current) => ({
                  ...current,
                  dueAt: event.target.value,
                }))
              }
            />
          </label>
          <label>
            Thời gian dự kiến (phút)
            <input
              type="number"
              min={1}
              value={form.estimatedMinutes}
              onChange={(event) =>
                setForm((current) => ({
                  ...current,
                  estimatedMinutes: Number(event.target.value),
                }))
              }
            />
          </label>
          <label>
            Nhắc trước (phút)
            <input
              type="number"
              min={0}
              disabled={
                !form.dueAt ||
                form.status === "COMPLETED" ||
                form.status === "CANCELLED"
              }
              value={form.reminderMinutesBefore}
              onChange={(event) =>
                setForm((current) => ({
                  ...current,
                  reminderMinutesBefore: Number(event.target.value),
                }))
              }
            />
          </label>
        </div>
        {!!tags.length && (
          <fieldset className="tag-picker">
            <legend>Nhãn</legend>
            {tags.map((tag) => (
              <label key={tag.id}>
                <input
                  type="checkbox"
                  checked={form.tagIds.includes(tag.id)}
                  onChange={() => toggleTag(tag.id)}
                />
                <span style={{ background: tag.color }} />
                {tag.name}
              </label>
            ))}
          </fieldset>
        )}
        {!task && (
          <label>
            Checklist ban đầu
            <textarea
              rows={3}
              value={form.subtasksText}
              onChange={(event) =>
                setForm((current) => ({
                  ...current,
                  subtasksText: event.target.value,
                }))
              }
              placeholder={"Mỗi dòng là một bước\nVí dụ: Thu thập tài liệu"}
            />
          </label>
        )}
        {task && (
          <fieldset className="checklist-editor">
            <legend>Checklist</legend>
            {subtasks.map((subtask) => (
              <div
                key={subtask.id}
                className={subtask.completed ? "completed" : ""}
              >
                <button
                  type="button"
                  className="check-toggle"
                  onClick={() => completeSubtask.mutate(subtask.id)}
                  aria-label={
                    subtask.completed
                      ? "Đánh dấu chưa hoàn thành"
                      : "Đánh dấu hoàn thành"
                  }
                >
                  {subtask.completed ? "✓" : ""}
                </button>
                {editingSubtask?.id === subtask.id ? (
                  <input
                    className="checklist-title-input"
                    value={editingSubtask.title}
                    onChange={(event) =>
                      setEditingSubtask({
                        id: subtask.id,
                        title: event.target.value,
                      })
                    }
                    aria-label={`Tên bước ${subtask.title}`}
                    maxLength={255}
                  />
                ) : (
                  <span>{subtask.title}</span>
                )}
                <div className="checklist-actions">
                  {editingSubtask?.id === subtask.id ? (
                    <>
                      <button
                        type="button"
                        className="icon-btn"
                        onClick={() =>
                          updateSubtask.mutate({
                            id: subtask.id,
                            title: editingSubtask.title,
                          })
                        }
                        disabled={
                          !editingSubtask.title.trim() ||
                          updateSubtask.isPending
                        }
                        aria-label={`Lưu ${subtask.title}`}
                      >
                        <Save size={14} />
                      </button>
                      <button
                        type="button"
                        className="icon-btn"
                        onClick={() => setEditingSubtask(undefined)}
                        aria-label={`Hủy sửa ${subtask.title}`}
                      >
                        <X size={14} />
                      </button>
                    </>
                  ) : (
                    <>
                      <button
                        type="button"
                        className="icon-btn"
                        onClick={() =>
                          setEditingSubtask({
                            id: subtask.id,
                            title: subtask.title,
                          })
                        }
                        aria-label={`Sửa ${subtask.title}`}
                      >
                        <Pencil size={14} />
                      </button>
                      <button
                        type="button"
                        className="icon-btn"
                        onClick={() => deleteSubtask.mutate(subtask.id)}
                        aria-label={`Xóa ${subtask.title}`}
                      >
                        <Trash2 size={14} />
                      </button>
                    </>
                  )}
                </div>
              </div>
            ))}
            <div className="checklist-add">
              <input
                value={newSubtask}
                onChange={(event) => setNewSubtask(event.target.value)}
                placeholder="Thêm bước mới"
                maxLength={255}
              />
              <button
                type="button"
                className="ghost"
                disabled={!newSubtask.trim() || addSubtask.isPending}
                onClick={() => addSubtask.mutate()}
              >
                Thêm bước
              </button>
            </div>
          </fieldset>
        )}
        {(clientError || save.isError) && (
          <p className="form-error" role="alert">
            {clientError || "Không thể lưu. Hãy kiểm tra dữ liệu và thử lại."}
          </p>
        )}
        <div className="modal-actions">
          {task && (
            <button
              type="button"
              className="danger-btn"
              disabled={remove.isPending}
              onClick={() => {
                if (window.confirm("Bạn có chắc muốn xóa công việc này?"))
                  remove.mutate();
              }}
            >
              <Trash2 size={16} />
              Xóa
            </button>
          )}
          <button type="button" className="ghost" onClick={onClose}>
            Hủy
          </button>
          <button className="primary" disabled={save.isPending}>
            {save.isPending ? "Đang lưu..." : "Lưu công việc"}
          </button>
        </div>
      </form>
    </div>
  );
}
