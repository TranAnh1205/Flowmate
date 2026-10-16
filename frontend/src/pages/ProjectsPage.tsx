import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { FolderKanban, Plus, TicketCheck, UsersRound } from "lucide-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, getApiErrorMessage } from "../api";
import type { ProjectInvitation, RecentBoard, TeamProject } from "../types";

export default function ProjectsPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const {
    data: projects = [],
    isLoading,
    error,
  } = useQuery({
    queryKey: ["projects"],
    queryFn: async () => (await api.get<TeamProject[]>("/projects")).data,
  });
  const { data: invitations = [] } = useQuery({
    queryKey: ["project-invitations"],
    queryFn: async () =>
      (await api.get<ProjectInvitation[]>("/projects/invitations")).data,
  });
  const { data: recentBoards = [] } = useQuery({
    queryKey: ["recent-boards"],
    queryFn: async () => (await api.get<RecentBoard[]>("/projects/recent-boards")).data,
  });
  const create = useMutation({
    mutationFn: async () =>
      (
        await api.post<TeamProject>("/projects", {
          name: name.trim(),
          description: description.trim() || null,
        })
      ).data,
    onSuccess: (project) => {
      queryClient.invalidateQueries({ queryKey: ["projects"] });
      navigate(`/app/projects/${project.id}`);
    },
  });
  const join = useMutation({
    mutationFn: async (invitationId: string) =>
      (
        await api.post<TeamProject>("/projects/invitations/accept", {
          invitationId,
        })
      ).data,
    onSuccess: (project) => {
      queryClient.invalidateQueries({ queryKey: ["projects"] });
      queryClient.invalidateQueries({ queryKey: ["project-invitations"] });
      navigate(`/app/projects/${project.id}`);
    },
  });
  const submitCreate = (event: FormEvent) => {
    event.preventDefault();
    if (name.trim()) create.mutate();
  };

  return (
    <div className="team-page">
      <div className="page-heading">
        <div>
          <span className="eyebrow">LÀM VIỆC CÙNG NHAU</span>
          <h1>Dự án nhóm</h1>
          <p>Tạo dự án, mời thành viên và theo dõi công việc được giao.</p>
        </div>
      </div>
      <div className="team-two-col">
        <form className="panel team-form" onSubmit={submitCreate}>
          <h2>
            <Plus size={19} /> Tạo dự án
          </h2>
          <label>
            Tên dự án
            <input
              value={name}
              onChange={(event) => setName(event.target.value)}
              maxLength={160}
              required
              placeholder="Ví dụ: Hoàn thành đồ án"
            />
          </label>
          <label>
            Mô tả
            <textarea
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              maxLength={5000}
              rows={3}
              placeholder="Mục tiêu và phạm vi của nhóm"
            />
          </label>
          <button
            className="primary"
            disabled={create.isPending || !name.trim()}
          >
            Tạo dự án
          </button>
          {create.isError && (
            <p className="form-error" role="alert">
              {getApiErrorMessage(create.error, "Không thể tạo dự án.")}
            </p>
          )}
        </form>
        <section className="panel team-form">
          <h2>
            <TicketCheck size={19} /> Lời mời của bạn
          </h2>
          <p>
            Chủ dự án mời bằng email đăng nhập của bạn. Lời mời có hiệu lực
            trong 7 ngày.
          </p>
          {invitations.length === 0 && <p>Chưa có lời mời nào.</p>}
          <div className="team-member-list">
            {invitations.map((invitation) => (
              <div className="team-member" key={invitation.id}>
                <span>
                  <strong>{invitation.projectName}</strong>
                  <small>
                    Hết hạn:{" "}
                    {new Date(invitation.expiresAt).toLocaleDateString("vi-VN")}
                  </small>
                </span>
                <button
                  type="button"
                  className="secondary"
                  disabled={join.isPending}
                  onClick={() => join.mutate(invitation.id)}
                >
                  Tham gia
                </button>
              </div>
            ))}
          </div>
          {join.isError && (
            <p className="form-error" role="alert">
              {getApiErrorMessage(join.error, "Không thể tham gia dự án.")}
            </p>
          )}
        </section>
      </div>
      {recentBoards.length > 0 && (
        <section className="team-section">
          <h2>Bảng đã mở gần đây</h2>
          <div className="team-board-links">
            {recentBoards.map(({ board, projectName, viewedAt }) => (
              <Link className="panel team-board-link" key={board.id}
                to={`/app/projects/${board.projectId}/boards/${board.id}`}>
                <strong>{board.title}</strong>
                <span>{projectName} · {new Date(viewedAt).toLocaleDateString("vi-VN")}</span>
              </Link>
            ))}
          </div>
        </section>
      )}
      <section className="team-section">
        <h2>Dự án của bạn</h2>
        {isLoading && <p>Đang tải dự án...</p>}
        {error && (
          <p className="form-error" role="alert">
            {getApiErrorMessage(error, "Không thể tải dự án.")}
          </p>
        )}
        {!isLoading && !error && projects.length === 0 && (
          <div className="panel team-empty">
            Bạn chưa tham gia dự án nào. Hãy tạo dự án hoặc chấp nhận lời mời.
          </div>
        )}
        <div className="team-project-grid">
          {projects.map((project) => {
            const percent = project.taskCount
              ? Math.round(
                  (project.completedTaskCount * 100) / project.taskCount,
                )
              : 0;
            return (
              <Link
                className="panel team-project-card"
                key={project.id}
                to={`/app/projects/${project.id}`}
              >
                <div className="team-card-heading">
                  <FolderKanban size={22} />
                  <span>
                    {project.status === "ARCHIVED"
                      ? "Đã lưu trữ"
                      : project.myRole === "OWNER"
                        ? "Chủ dự án"
                        : "Thành viên"}
                  </span>
                </div>
                <h3>{project.name}</h3>
                <p>{project.description || "Chưa có mô tả."}</p>
                <div className="team-meta">
                  <span>
                    <UsersRound size={16} /> {project.memberCount} thành viên
                  </span>
                  <span>
                    {project.completedTaskCount}/{project.taskCount} việc hoàn
                    thành
                  </span>
                </div>
                <div
                  className="team-progress"
                  role="progressbar"
                  aria-label={`Tiến độ ${project.name}`}
                  aria-valuenow={percent}
                  aria-valuemin={0}
                  aria-valuemax={100}
                >
                  <span style={{ width: `${percent}%` }} />
                </div>
              </Link>
            );
          })}
        </div>
      </section>
    </div>
  );
}
