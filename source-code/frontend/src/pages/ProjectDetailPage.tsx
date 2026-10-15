import { useEffect, useState, type FormEvent } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ArrowLeft, Plus, Trash2, UsersRound } from "lucide-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, getApiErrorMessage } from "../api";
import { useAuth } from "../auth";
import type {
  Priority,
  ProjectBoard,
  ProjectInvitation,
  ProjectMember,
  ProjectTask,
  ProjectTaskPage,
  Status,
  TeamProject,
} from "../types";

const statuses: { value: Status; label: string }[] = [
  { value: "TODO", label: "Cần làm" },
  { value: "IN_PROGRESS", label: "Đang làm" },
  { value: "COMPLETED", label: "Hoàn thành" },
  { value: "CANCELLED", label: "Đã hủy" },
];
const priorities: { value: Priority; label: string }[] = [
  { value: "LOW", label: "Thấp" },
  { value: "MEDIUM", label: "Vừa" },
  { value: "HIGH", label: "Cao" },
  { value: "URGENT", label: "Khẩn cấp" },
];

type TaskForm = {
  title: string;
  description: string;
  priority: Priority;
  status: Status;
  assigneeId: string;
  startAt: string;
  dueAt: string;
};
const emptyTask: TaskForm = {
  title: "",
  description: "",
  priority: "MEDIUM",
  status: "TODO",
  assigneeId: "",
  startAt: "",
  dueAt: "",
};

function localDateTime(value?: string) {
  if (!value) return "";
  const date = new Date(value);
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000)
    .toISOString()
    .slice(0, 16);
}
function taskPayload(form: TaskForm) {
  return {
    ...form,
    title: form.title.trim(),
    description: form.description.trim() || null,
    assigneeId: form.assigneeId || null,
    startAt: form.startAt ? new Date(form.startAt).toISOString() : null,
    dueAt: form.dueAt ? new Date(form.dueAt).toISOString() : null,
  };
}

export default function ProjectDetailPage() {
  const { projectId = "" } = useParams();
  const { user } = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [createForm, setCreateForm] = useState<TaskForm>(emptyTask);
  const [editing, setEditing] = useState<ProjectTask>();
  const [editForm, setEditForm] = useState<TaskForm>(emptyTask);
  const [inviteEmail, setInviteEmail] = useState("");
  const [invitation, setInvitation] = useState<ProjectInvitation>();
  const [projectName, setProjectName] = useState("");
  const [projectDescription, setProjectDescription] = useState("");
  const [boardTitle, setBoardTitle] = useState("");
  const [boardTemplate, setBoardTemplate] = useState<"basic" | "sprint" | "content">("basic");
  const refresh = () =>
    queryClient.invalidateQueries({ queryKey: ["projects"] });
  const {
    data: project,
    isLoading,
    error,
  } = useQuery({
    queryKey: ["projects", projectId],
    queryFn: async () =>
      (await api.get<TeamProject>(`/projects/${projectId}`)).data,
    enabled: !!projectId,
  });
  const { data: members = [] } = useQuery({
    queryKey: ["projects", projectId, "members"],
    queryFn: async () =>
      (await api.get<ProjectMember[]>(`/projects/${projectId}/members`)).data,
    enabled: !!projectId,
  });
  const { data: boards = [], error: boardsError } = useQuery({
    queryKey: ["projects", projectId, "boards"],
    queryFn: async () =>
      (await api.get<ProjectBoard[]>(`/projects/${projectId}/boards`)).data,
    enabled: !!projectId,
  });
  const {
    data: taskPage,
    isLoading: tasksLoading,
    error: tasksError,
  } = useQuery({
    queryKey: ["projects", projectId, "tasks", page],
    queryFn: async () =>
      (
        await api.get<ProjectTaskPage>(`/projects/${projectId}/tasks`, {
          params: { page, size: 20 },
        })
      ).data,
    enabled: !!projectId,
  });
  useEffect(() => {
    if (project) {
      setProjectName(project.name);
      setProjectDescription(project.description || "");
    }
  }, [project?.id, project?.name, project?.description]);
  const isOwner = project?.myRole === "OWNER";
  const isArchived = project?.status === "ARCHIVED";
  const createBoard = useMutation({
    mutationFn: () =>
      api.post(`/projects/${projectId}/boards`, {
        title: boardTitle.trim(),
        visibility: "WORKSPACE",
        background: "indigo",
        template: boardTemplate,
      }),
    onSuccess: () => {
      setBoardTitle("");
      setBoardTemplate("basic");
      queryClient.invalidateQueries({ queryKey: ["projects", projectId, "boards"] });
    },
  });
  const createTask = useMutation({
    mutationFn: () =>
      api.post(
        `/projects/${projectId}/tasks`,
        taskPayload({
          ...createForm,
          assigneeId: isOwner ? createForm.assigneeId : user?.id || "",
        }),
      ),
    onSuccess: () => {
      setCreateForm(emptyTask);
      setPage(0);
      refresh();
    },
  });
  const updateTask = useMutation({
    mutationFn: () =>
      api.put(`/projects/${projectId}/tasks/${editing?.id}`, {
        ...taskPayload(editForm),
        version: editing?.version,
      }),
    onSuccess: () => {
      setEditing(undefined);
      refresh();
    },
  });
  const changeStatus = useMutation({
    mutationFn: ({ task, status }: { task: ProjectTask; status: Status }) =>
      api.patch(`/projects/${projectId}/tasks/${task.id}/status`, {
        status,
        version: task.version,
      }),
    onSuccess: refresh,
  });
  const deleteTask = useMutation({
    mutationFn: (taskId: string) =>
      api.delete(`/projects/${projectId}/tasks/${taskId}`),
    onSuccess: refresh,
  });
  const invite = useMutation({
    mutationFn: async () =>
      (
        await api.post<ProjectInvitation>(
          `/projects/${projectId}/invitations`,
          { email: inviteEmail.trim() },
        )
      ).data,
    onSuccess: (value) => {
      setInvitation(value);
      setInviteEmail("");
    },
  });
  const updateProject = useMutation({
    mutationFn: (status: "ACTIVE" | "ARCHIVED") =>
      api.put(`/projects/${projectId}`, {
        name: projectName.trim(),
        description: projectDescription.trim() || null,
        status,
      }),
    onSuccess: refresh,
  });
  const removeMember = useMutation({
    mutationFn: (memberId: string) =>
      api.delete(`/projects/${projectId}/members/${memberId}`),
    onSuccess: refresh,
  });
  const leave = useMutation({
    mutationFn: () => api.delete(`/projects/${projectId}/members/me`),
    onSuccess: () => {
      refresh();
      navigate("/app/projects");
    },
  });
  const startEdit = (task: ProjectTask) => {
    setEditing(task);
    setEditForm({
      title: task.title,
      description: task.description || "",
      priority: task.priority,
      status: task.status,
      assigneeId: task.assigneeId || "",
      startAt: localDateTime(task.startAt),
      dueAt: localDateTime(task.dueAt),
    });
  };
  const submitCreate = (event: FormEvent) => {
    event.preventDefault();
    if (createForm.title.trim()) createTask.mutate();
  };
  const submitEdit = (event: FormEvent) => {
    event.preventDefault();
    if (editForm.title.trim()) updateTask.mutate();
  };
  const memberName = (id?: string) =>
    id
      ? members.find((member) => member.userId === id)?.email || "Thành viên cũ"
      : "Chưa giao";
  const operationError =
    createTask.error ||
    updateTask.error ||
    changeStatus.error ||
    deleteTask.error ||
    invite.error ||
    updateProject.error ||
    removeMember.error ||
    leave.error ||
    createBoard.error;

  if (isLoading) return <div className="team-page">Đang tải dự án...</div>;
  if (error || !project)
    return (
      <div className="team-page">
        <Link to="/app/projects">← Dự án nhóm</Link>
        <p className="form-error" role="alert">
          {getApiErrorMessage(error, "Không thể tải dự án.")}
        </p>
      </div>
    );

  return (
    <div className="team-page">
      <Link className="team-back" to="/app/projects">
        <ArrowLeft size={17} /> Dự án nhóm
      </Link>
      <div className="page-heading">
        <div>
          <span className="eyebrow">
            {isArchived ? "ĐÃ LƯU TRỮ" : "DỰ ÁN NHÓM"}
          </span>
          <h1>{project.name}</h1>
          <p>
            {project.description ||
              "Cùng nhóm hoàn thành những việc quan trọng."}
          </p>
        </div>
      </div>
      <div className="team-stats">
        <div className="panel">
          <strong>{project.memberCount}</strong>
          <span>Thành viên</span>
        </div>
        <div className="panel">
          <strong>{project.taskCount}</strong>
          <span>Công việc</span>
        </div>
        <div className="panel">
          <strong>{project.completedTaskCount}</strong>
          <span>Đã hoàn thành</span>
        </div>
        <div className="panel">
          <strong>
            {project.taskCount
              ? Math.round(
                  (project.completedTaskCount * 100) / project.taskCount,
                )
              : 0}
            %
          </strong>
          <span>Tiến độ</span>
        </div>
      </div>
      <section className="team-section">
        <div className="team-section-heading">
          <h2>Bảng công việc</h2>
          <span>{boards.length} bảng</span>
        </div>
        {boardsError && (
          <p className="form-error" role="alert">
            {getApiErrorMessage(boardsError, "Không thể tải bảng công việc.")}
          </p>
        )}
        <div className="team-board-links">
          {boards.map((board) => (
            <Link
              className="panel team-board-link"
              key={board.id}
              to={`/app/projects/${projectId}/boards/${board.id}`}
            >
              <strong>{board.title}</strong>
              <span>{board.isDefault ? "Bảng mặc định" : "Bảng nhóm"}</span>
            </Link>
          ))}
        </div>
        {isOwner && !isArchived && (
          <form
            className="team-board-create"
            onSubmit={(event) => {
              event.preventDefault();
              if (boardTitle.trim()) createBoard.mutate();
            }}
          >
            <input
              aria-label="Tên bảng mới"
              value={boardTitle}
              onChange={(event) => setBoardTitle(event.target.value)}
              maxLength={160}
              placeholder="Tên bảng mới"
            />
            <select aria-label="Mẫu bảng" value={boardTemplate}
              onChange={(event) => setBoardTemplate(event.target.value as "basic" | "sprint" | "content")}>
              <option value="basic">Cơ bản</option>
              <option value="sprint">Sprint phần mềm</option>
              <option value="content">Nội dung</option>
            </select>
            <button className="secondary" disabled={!boardTitle.trim() || createBoard.isPending}>
              <Plus size={16} /> Tạo bảng
            </button>
          </form>
        )}
      </section>
      {operationError && (
        <p className="form-error" role="alert">
          {getApiErrorMessage(operationError, "Không thể thực hiện thao tác.")}
        </p>
      )}
      <div className="team-two-col">
        <section className="panel team-form">
          <h2>
            <UsersRound size={19} /> Thành viên
          </h2>
          <div className="team-member-list">
            {members.map((member) => (
              <div key={member.userId} className="team-member">
                <span>
                  <strong>{member.email}</strong>
                  <small>
                    {member.role === "OWNER" ? "Chủ dự án" : "Thành viên"}
                  </small>
                </span>
                {isOwner && member.role !== "OWNER" && (
                  <button
                    className="icon-btn"
                    type="button"
                    title="Xóa thành viên"
                    aria-label={`Xóa ${member.email}`}
                    onClick={() => {
                      if (window.confirm(`Xóa ${member.email} khỏi dự án?`))
                        removeMember.mutate(member.userId);
                    }}
                  >
                    <Trash2 size={17} />
                  </button>
                )}
              </div>
            ))}
          </div>
          {isOwner && !isArchived && (
            <>
              <form
                className="team-invite-form"
                onSubmit={(event) => {
                  event.preventDefault();
                  if (inviteEmail.trim()) invite.mutate();
                }}
              >
                <label>
                  Email thành viên
                  <input
                    type="email"
                    value={inviteEmail}
                    onChange={(event) => setInviteEmail(event.target.value)}
                    maxLength={255}
                    required
                    placeholder="thanhvien@example.com"
                  />
                </label>
                <button
                  className="secondary"
                  disabled={invite.isPending || !inviteEmail.trim()}
                >
                  Gửi lời mời
                </button>
              </form>
              {invitation && (
                <div className="team-invite">
                  <p>
                    Đã tạo lời mời cho {invitation.invitedEmail}. Người này sẽ
                    thấy lời mời khi đăng nhập bằng email đó. Hết hạn sau 7
                    ngày.
                  </p>
                </div>
              )}
            </>
          )}
          {!isOwner && (
            <button
              className="secondary"
              type="button"
              onClick={() => {
                if (window.confirm("Rời dự án này?")) leave.mutate();
              }}
              disabled={leave.isPending}
            >
              Rời dự án
            </button>
          )}
        </section>
        {isOwner && (
          <form
            className="panel team-form"
            onSubmit={(event) => {
              event.preventDefault();
              if (projectName.trim()) updateProject.mutate(project.status);
            }}
          >
            <h2>Quản lý dự án</h2>
            <label>
              Tên dự án
              <input
                value={projectName}
                onChange={(event) => setProjectName(event.target.value)}
                maxLength={160}
                required
              />
            </label>
            <label>
              Mô tả
              <textarea
                value={projectDescription}
                onChange={(event) => setProjectDescription(event.target.value)}
                maxLength={5000}
                rows={3}
              />
            </label>
            <div className="team-actions">
              <button
                className="primary"
                disabled={updateProject.isPending || !projectName.trim()}
              >
                Lưu thông tin
              </button>
              <button
                className="secondary"
                type="button"
                disabled={updateProject.isPending}
                onClick={() =>
                  updateProject.mutate(isArchived ? "ACTIVE" : "ARCHIVED")
                }
              >
                {isArchived ? "Mở lại" : "Lưu trữ"}
              </button>
            </div>
          </form>
        )}
      </div>
      {!isArchived && (
        <form
          className="panel team-form team-task-form"
          onSubmit={submitCreate}
        >
          <h2>
            <Plus size={19} /> Tạo công việc nhóm
          </h2>
          <div className="team-form-grid">
            <label>
              Tên công việc
              <input
                value={createForm.title}
                onChange={(event) =>
                  setCreateForm({ ...createForm, title: event.target.value })
                }
                maxLength={255}
                required
                placeholder="Công việc cần hoàn thành"
              />
            </label>
            <label>
              Người thực hiện
              <select
                value={isOwner ? createForm.assigneeId : user?.id || ""}
                onChange={(event) =>
                  setCreateForm({
                    ...createForm,
                    assigneeId: event.target.value,
                  })
                }
                disabled={!isOwner}
              >
                {isOwner && <option value="">Giao cho bản thân</option>}
                {members.map((member) => (
                  <option key={member.userId} value={member.userId}>
                    {member.email}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Mức ưu tiên
              <select
                value={createForm.priority}
                onChange={(event) =>
                  setCreateForm({
                    ...createForm,
                    priority: event.target.value as Priority,
                  })
                }
              >
                {priorities.map((item) => (
                  <option key={item.value} value={item.value}>
                    {item.label}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Bắt đầu
              <input type="datetime-local" value={createForm.startAt}
                max={createForm.dueAt || undefined}
                onChange={(event) => setCreateForm({ ...createForm, startAt: event.target.value })} />
            </label>
            <label>
              Hạn chót
              <input
                type="datetime-local"
                value={createForm.dueAt}
                min={createForm.startAt || undefined}
                onChange={(event) =>
                  setCreateForm({ ...createForm, dueAt: event.target.value })
                }
              />
            </label>
          </div>
          <label>
            Mô tả
            <textarea
              value={createForm.description}
              onChange={(event) =>
                setCreateForm({
                  ...createForm,
                  description: event.target.value,
                })
              }
              maxLength={10000}
              rows={2}
            />
          </label>
          <button
            className="primary"
            disabled={createTask.isPending || !createForm.title.trim()}
          >
            Thêm công việc
          </button>
        </form>
      )}
      <section className="team-section">
        <div className="team-section-heading">
          <h2>Công việc của nhóm</h2>
          <span>{taskPage?.totalElements ?? 0} công việc</span>
        </div>
        {tasksLoading && <p>Đang tải công việc...</p>}
        {tasksError && (
          <p className="form-error" role="alert">
            {getApiErrorMessage(tasksError, "Không thể tải công việc.")}
          </p>
        )}
        {!tasksLoading && taskPage?.content.length === 0 && (
          <div className="panel team-empty">
            Chưa có công việc nào trong dự án.
          </div>
        )}
        <div className="team-task-list">
          {taskPage?.content.map((task) => {
            const canEdit =
              !isArchived &&
              (isOwner ||
                task.createdBy === user?.id ||
                task.assigneeId === user?.id);
            const canDelete =
              !isArchived && (isOwner || task.createdBy === user?.id);
            return (
              <article className="panel team-task" key={task.id}>
                <div className="team-task-main">
                  <h3>{task.title}</h3>
                  {task.description && <p>{task.description}</p>}
                  <div className="team-task-meta">
                    <span>{memberName(task.assigneeId)}</span>
                    <span>
                      {
                        priorities.find((item) => item.value === task.priority)
                          ?.label
                      }
                    </span>
                    {task.dueAt && (
                      <span>
                        Hạn: {new Date(task.dueAt).toLocaleString("vi-VN")}
                      </span>
                    )}
                  </div>
                </div>
                <div className="team-task-actions">
                  <select
                    aria-label={`Trạng thái ${task.title}`}
                    value={task.status}
                    disabled={!canEdit || changeStatus.isPending}
                    onChange={(event) =>
                      changeStatus.mutate({
                        task,
                        status: event.target.value as Status,
                      })
                    }
                  >
                    {statuses.map((item) => (
                      <option key={item.value} value={item.value}>
                        {item.label}
                      </option>
                    ))}
                  </select>
                  {canEdit && (
                    <button
                      type="button"
                      className="secondary"
                      onClick={() => startEdit(task)}
                    >
                      Sửa
                    </button>
                  )}
                  {canDelete && (
                    <button
                      type="button"
                      className="icon-btn"
                      title="Xóa công việc"
                      aria-label={`Xóa ${task.title}`}
                      onClick={() => {
                        if (window.confirm(`Xóa công việc "${task.title}"?`))
                          deleteTask.mutate(task.id);
                      }}
                    >
                      <Trash2 size={17} />
                    </button>
                  )}
                </div>
              </article>
            );
          })}
        </div>
        {!!taskPage && taskPage.totalPages > 1 && (
          <div className="team-pagination">
            <button
              type="button"
              className="secondary"
              disabled={page === 0}
              onClick={() => setPage(page - 1)}
            >
              Trang trước
            </button>
            <span>
              Trang {page + 1}/{taskPage.totalPages}
            </span>
            <button
              type="button"
              className="secondary"
              disabled={page + 1 >= taskPage.totalPages}
              onClick={() => setPage(page + 1)}
            >
              Trang sau
            </button>
          </div>
        )}
      </section>
      {editing && (
        <div
          className="team-dialog-backdrop"
          role="presentation"
          onMouseDown={() => setEditing(undefined)}
        >
          <form
            className="panel team-form team-dialog"
            role="dialog"
            aria-modal="true"
            aria-label="Sửa công việc nhóm"
            onMouseDown={(event) => event.stopPropagation()}
            onSubmit={submitEdit}
          >
            <h2>Sửa công việc</h2>
            <label>
              Tên công việc
              <input
                value={editForm.title}
                onChange={(event) =>
                  setEditForm({ ...editForm, title: event.target.value })
                }
                required
                maxLength={255}
              />
            </label>
            <label>
              Mô tả
              <textarea
                value={editForm.description}
                onChange={(event) =>
                  setEditForm({ ...editForm, description: event.target.value })
                }
                rows={3}
                maxLength={10000}
              />
            </label>
            <div className="team-form-grid">
              <label>
                Trạng thái
                <select
                  value={editForm.status}
                  onChange={(event) =>
                    setEditForm({
                      ...editForm,
                      status: event.target.value as Status,
                    })
                  }
                >
                  {statuses.map((item) => (
                    <option key={item.value} value={item.value}>
                      {item.label}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Ưu tiên
                <select
                  value={editForm.priority}
                  onChange={(event) =>
                    setEditForm({
                      ...editForm,
                      priority: event.target.value as Priority,
                    })
                  }
                >
                  {priorities.map((item) => (
                    <option key={item.value} value={item.value}>
                      {item.label}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Người thực hiện
                <select
                  value={editForm.assigneeId}
                  disabled={!isOwner}
                  onChange={(event) =>
                    setEditForm({ ...editForm, assigneeId: event.target.value })
                  }
                >
                  <option value="">Chưa giao</option>
                  {members.map((member) => (
                    <option key={member.userId} value={member.userId}>
                      {member.email}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Bắt đầu
                <input type="datetime-local" value={editForm.startAt}
                  max={editForm.dueAt || undefined}
                  onChange={(event) => setEditForm({ ...editForm, startAt: event.target.value })} />
              </label>
              <label>
                Hạn chót
                <input
                  type="datetime-local"
                  value={editForm.dueAt}
                  min={editForm.startAt || undefined}
                  onChange={(event) =>
                    setEditForm({ ...editForm, dueAt: event.target.value })
                  }
                />
              </label>
            </div>
            <div className="team-actions">
              <button
                className="primary"
                disabled={updateTask.isPending || !editForm.title.trim()}
              >
                Lưu
              </button>
              <button
                className="secondary"
                type="button"
                onClick={() => setEditing(undefined)}
              >
                Hủy
              </button>
            </div>
            {updateTask.isError && (
              <p className="form-error" role="alert">
                {getApiErrorMessage(
                  updateTask.error,
                  "Không thể sửa công việc.",
                )}
              </p>
            )}
          </form>
        </div>
      )}
    </div>
  );
}
