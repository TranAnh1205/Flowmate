import { useState } from "react";
import { Flag, Pencil, Plus, Save, Target, Trash2, X } from "lucide-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, getApiErrorMessage } from "../api";
import type { TaskPage } from "../types";

type Goal = {
  id: string;
  title: string;
  description?: string;
  status: "ACTIVE" | "COMPLETED" | "ARCHIVED";
  targetDate?: string;
};

type GoalForm = {
  title: string;
  description: string;
  status: Goal["status"];
  targetDate: string;
};

export default function GoalsPage() {
  const [title, setTitle] = useState("");
  const [editingId, setEditingId] = useState<string>();
  const [editForm, setEditForm] = useState<GoalForm>({
    title: "",
    description: "",
    status: "ACTIVE",
    targetDate: "",
  });
  const queryClient = useQueryClient();
  const {
    data = [],
    isError,
    error,
  } = useQuery({
    queryKey: ["goals"],
    queryFn: async () => (await api.get<Goal[]>("/goals")).data,
  });
  const { data: tasks } = useQuery({
    queryKey: ["tasks", "goal-progress"],
    queryFn: async () =>
      (await api.get<TaskPage>("/tasks", { params: { size: 100 } })).data
        .content,
  });
  const create = useMutation({
    mutationFn: () =>
      api.post("/goals", {
        title: title.trim(),
        description: "",
        status: "ACTIVE",
        targetDate: null,
      }),
    onSuccess: () => {
      setTitle("");
      queryClient.invalidateQueries({ queryKey: ["goals"] });
    },
  });
  const update = useMutation({
    mutationFn: () =>
      api.put(`/goals/${editingId}`, {
        ...editForm,
        title: editForm.title.trim(),
        description: editForm.description || null,
        targetDate: editForm.targetDate || null,
      }),
    onSuccess: () => {
      setEditingId(undefined);
      queryClient.invalidateQueries({ queryKey: ["goals"] });
    },
  });
  const remove = useMutation({
    mutationFn: (id: string) => api.delete(`/goals/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["goals"] });
      queryClient.invalidateQueries({ queryKey: ["tasks"] });
    },
  });
  const startEdit = (goal: Goal) => {
    setEditingId(goal.id);
    setEditForm({
      title: goal.title,
      description: goal.description || "",
      status: goal.status,
      targetDate: goal.targetDate || "",
    });
  };
  const progressFor = (goalId: string) => {
    const related = tasks?.filter((task) => task.goalId === goalId) || [];
    if (!related.length) return { percent: 0, completed: 0, total: 0 };
    const completed = related.filter(
      (task) => task.status === "COMPLETED",
    ).length;
    return {
      percent: Math.round((completed * 100) / related.length),
      completed,
      total: related.length,
    };
  };

  return (
    <>
      <div className="page-heading">
        <div>
          <span className="eyebrow">HƯỚNG ĐI DÀI HẠN</span>
          <h1>Mục tiêu cá nhân</h1>
          <p>Gắn những công việc nhỏ với điều bạn thực sự muốn đạt được.</p>
        </div>
      </div>
      <form
        className="panel goal-create"
        onSubmit={(event) => {
          event.preventDefault();
          if (title.trim()) create.mutate();
        }}
      >
        <Target />
        <input
          value={title}
          onChange={(event) => setTitle(event.target.value)}
          placeholder="Ví dụ: Hoàn thành đồ án tốt nghiệp"
          maxLength={160}
        />
        <button
          className="primary"
          disabled={!title.trim() || create.isPending}
        >
          <Plus size={17} />
          Thêm mục tiêu
        </button>
      </form>
      {(create.isError || update.isError || remove.isError || isError) && (
        <div className="form-error goal-error" role="alert">
          {getApiErrorMessage(
            create.error || update.error || remove.error || error,
            "Không thể xử lý mục tiêu.",
          )}
        </div>
      )}
      <div className="goal-grid">
        {data.map((goal) => {
          const progress = progressFor(goal.id);
          return (
            <article className="panel goal-card" key={goal.id}>
              {editingId === goal.id ? (
                <div className="goal-edit-form">
                  <label>
                    Tên mục tiêu
                    <input
                      value={editForm.title}
                      onChange={(event) =>
                        setEditForm((current) => ({
                          ...current,
                          title: event.target.value,
                        }))
                      }
                    />
                  </label>
                  <label>
                    Mô tả
                    <textarea
                      rows={3}
                      value={editForm.description}
                      onChange={(event) =>
                        setEditForm((current) => ({
                          ...current,
                          description: event.target.value,
                        }))
                      }
                    />
                  </label>
                  <div className="form-grid">
                    <label>
                      Trạng thái
                      <select
                        value={editForm.status}
                        onChange={(event) =>
                          setEditForm((current) => ({
                            ...current,
                            status: event.target.value as Goal["status"],
                          }))
                        }
                      >
                        <option value="ACTIVE">Đang thực hiện</option>
                        <option value="COMPLETED">Hoàn thành</option>
                        <option value="ARCHIVED">Lưu trữ</option>
                      </select>
                    </label>
                    <label>
                      Ngày mục tiêu
                      <input
                        type="date"
                        value={editForm.targetDate}
                        onChange={(event) =>
                          setEditForm((current) => ({
                            ...current,
                            targetDate: event.target.value,
                          }))
                        }
                      />
                    </label>
                  </div>
                  <div className="goal-actions">
                    <button
                      className="ghost"
                      type="button"
                      onClick={() => setEditingId(undefined)}
                    >
                      <X size={15} /> Hủy
                    </button>
                    <button
                      className="primary"
                      type="button"
                      disabled={!editForm.title.trim() || update.isPending}
                      onClick={() => update.mutate()}
                    >
                      <Save size={15} /> Lưu
                    </button>
                  </div>
                </div>
              ) : (
                <>
                  <div className="goal-icon">
                    <Flag />
                  </div>
                  <span className={`status-pill ${goal.status.toLowerCase()}`}>
                    {goal.status}
                  </span>
                  <h2>{goal.title}</h2>
                  <p>
                    {goal.description ||
                      "Chưa có mô tả. Hãy thêm các công việc để tiến gần mục tiêu này."}
                  </p>
                  {goal.targetDate && (
                    <small>
                      Mục tiêu:{" "}
                      {new Date(goal.targetDate).toLocaleDateString("vi-VN")}
                    </small>
                  )}
                  <div className="goal-progress">
                    <div>
                      <i style={{ width: `${progress.percent}%` }} />
                    </div>
                    <span>{progress.percent}%</span>
                  </div>
                  <small>
                    {progress.completed}/{progress.total} công việc hoàn thành
                  </small>
                  <div className="goal-actions">
                    <button
                      className="ghost"
                      onClick={() => startEdit(goal)}
                      aria-label={`Sửa ${goal.title}`}
                    >
                      <Pencil size={15} /> Sửa
                    </button>
                    <button
                      className="danger-btn"
                      onClick={() => {
                        if (window.confirm(`Xóa mục tiêu “${goal.title}”?`)) {
                          remove.mutate(goal.id);
                        }
                      }}
                      aria-label={`Xóa ${goal.title}`}
                    >
                      <Trash2 size={15} /> Xóa
                    </button>
                  </div>
                </>
              )}
            </article>
          );
        })}
        {!data.length && (
          <div className="panel empty-state">
            <Target />
            <h3>Bắt đầu bằng một mục tiêu có ý nghĩa</h3>
            <p>
              Mục tiêu giúp bạn thấy các công việc hôm nay đang dẫn mình đi đâu.
            </p>
          </div>
        )}
      </div>
    </>
  );
}
