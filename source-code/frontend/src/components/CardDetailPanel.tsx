import { useEffect, useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Paperclip, X } from "lucide-react";
import { api, getApiErrorMessage } from "../api";
import { useAuth } from "../auth";
import type {
  BoardCard,
  BoardLabel,
  BoardEvent,
  CardAttachment,
  CardChecklistItem,
  CardComment,
  ProjectTask,
  ProjectBoard,
  ProjectBoardDetails,
} from "../types";

function toDateTimeInput(value?: string) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  return new Date(date.getTime() - date.getTimezoneOffset() * 60_000)
    .toISOString().slice(0, 16);
}

export default function CardDetailPanel({
  projectId,
  boardId,
  card,
  canEdit,
  canManageTasks,
  listId,
  onClose,
}: {
  projectId: string;
  boardId: string;
  card: BoardCard;
  canEdit: boolean;
  canManageTasks: boolean;
  listId: string;
  onClose: () => void;
}) {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const [commentText, setCommentText] = useState("");
  const [checkTitle, setCheckTitle] = useState("");
  const [labelName, setLabelName] = useState("");
  const [file, setFile] = useState<File>();
  const [editTitle, setEditTitle] = useState(card.title);
  const [editDescription, setEditDescription] = useState(card.description || "");
  const [startInput, setStartInput] = useState(toDateTimeInput(card.startAt));
  const [dueInput, setDueInput] = useState(toDateTimeInput(card.dueAt));
  const [targetBoardId, setTargetBoardId] = useState(boardId);
  const [targetListId, setTargetListId] = useState(listId);
  const [shareFeedback, setShareFeedback] = useState("");
  const root = `/projects/${projectId}/boards/${boardId}`;
  const cardPath = `${root}/cards/${card.id}`;
  const key = ["card-detail", projectId, boardId, card.id] as const;
  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: key });
    queryClient.invalidateQueries({ queryKey: ["project-board", projectId, boardId] });
  };
  const task = useQuery({
    queryKey: [...key, "task"],
    queryFn: async () => (await api.get<ProjectTask>(`/projects/${projectId}/tasks/${card.id}`)).data,
  });
  useEffect(() => {
    if (task.data) {
      setEditTitle(task.data.title);
      setEditDescription(task.data.description || "");
      setStartInput(toDateTimeInput(task.data.startAt));
      setDueInput(toDateTimeInput(task.data.dueAt));
    }
  }, [task.data?.id, task.data?.version]);
  const canEditTask = canEdit && !!task.data &&
    (canManageTasks || task.data.createdBy === user?.id || task.data.assigneeId === user?.id);
  const saveTask = useMutation({
    mutationFn: () => api.put(`${cardPath}`, {
      title: editTitle.trim(),
      description: editDescription.trim() || null,
      status: task.data?.status,
      priority: task.data?.priority,
      assigneeId: task.data?.assigneeId || null,
      startAt: startInput ? new Date(startInput).toISOString() : null,
      dueAt: dueInput ? new Date(dueInput).toISOString() : null,
      version: task.data?.version,
    }),
    onSuccess: refresh,
  });
  const events = useQuery({
    queryKey: ["board-events", projectId, boardId, "card", card.id],
    queryFn: async () => (await api.get<BoardEvent[]>(`${root}/events`)).data
      .filter((event) => event.taskId === card.id),
    refetchInterval: 3000,
  });
  const boards = useQuery({
    queryKey: ["projects", projectId, "boards"],
    queryFn: async () => (await api.get<ProjectBoard[]>(`/projects/${projectId}/boards`)).data,
  });
  const targetBoard = useQuery({
    queryKey: ["project-board", projectId, targetBoardId],
    queryFn: async () => (await api.get<ProjectBoardDetails>(
      `/projects/${projectId}/boards/${targetBoardId}`)).data,
    enabled: !!targetBoardId,
  });
  useEffect(() => {
    if (targetBoard.data && !targetBoard.data.lists.some((list) => list.id === targetListId))
      setTargetListId(targetBoard.data.lists[0]?.id || "");
  }, [targetBoard.data?.board.id, targetBoard.data?.lists, targetListId]);
  const copyCard = useMutation({
    mutationFn: () => api.post(`${cardPath}/copy`, { targetBoardId, listId: targetListId }),
    onSuccess: refresh,
  });
  const transferCard = useMutation({
    mutationFn: () => api.post(`${cardPath}/transfer`, {
      targetBoardId, listId: targetListId, version: task.data?.version,
    }),
    onSuccess: () => {
      refresh();
      onClose();
    },
  });
  const archiveCard = useMutation({
    mutationFn: () => api.delete(cardPath),
    onSuccess: () => {
      refresh();
      onClose();
    },
  });
  const comments = useQuery({
    queryKey: [...key, "comments"],
    queryFn: async () => (await api.get<CardComment[]>(`${cardPath}/comments`)).data,
    refetchInterval: 3000,
  });
  const checklist = useQuery({
    queryKey: [...key, "checklist"],
    queryFn: async () => (await api.get<CardChecklistItem[]>(`${cardPath}/checklist`)).data,
    refetchInterval: 3000,
  });
  const attachments = useQuery({
    queryKey: [...key, "attachments"],
    queryFn: async () => (await api.get<CardAttachment[]>(`${cardPath}/attachments`)).data,
  });
  const labels = useQuery({
    queryKey: [...key, "labels"],
    queryFn: async () => (await api.get<BoardLabel[]>(`${cardPath}/labels`)).data,
  });
  const boardLabels = useQuery({
    queryKey: ["board-labels", projectId, boardId],
    queryFn: async () => (await api.get<BoardLabel[]>(`${root}/labels`)).data,
  });
  const watchers = useQuery({
    queryKey: [...key, "watchers"],
    queryFn: async () => (await api.get<string[]>(`${cardPath}/watchers`)).data,
  });
  const addComment = useMutation({
    mutationFn: (body: string) => api.post(`${cardPath}/comments`, { body }),
    onSuccess: () => {
      setCommentText("");
      refresh();
    },
  });
  const addCheck = useMutation({
    mutationFn: (title: string) => api.post(`${cardPath}/checklist`, { title }),
    onSuccess: () => {
      setCheckTitle("");
      refresh();
    },
  });
  const toggleCheck = useMutation({
    mutationFn: (item: CardChecklistItem) =>
      api.put(`${cardPath}/checklist/${item.id}`, {
        title: item.title,
        done: !item.done,
      }),
    onSuccess: refresh,
  });
  const upload = useMutation({
    mutationFn: (value: File) => {
      const data = new FormData();
      data.append("file", value);
      return api.post(`${cardPath}/attachments`, data, {
        headers: { "Content-Type": "multipart/form-data" },
      });
    },
    onSuccess: () => {
      setFile(undefined);
      refresh();
    },
  });
  const addLabel = useMutation({
    mutationFn: (name: string) => api.post<BoardLabel>(`${root}/labels`, { name, color: "blue" }),
    onSuccess: async (response) => {
      await api.post(`${cardPath}/labels/${response.data.id}`);
      setLabelName("");
      queryClient.invalidateQueries({ queryKey: ["board-labels", projectId, boardId] });
      refresh();
    },
  });
  const attachLabel = useMutation({
    mutationFn: (labelId: string) => api.post(`${cardPath}/labels/${labelId}`),
    onSuccess: refresh,
  });
  const watch = useMutation({
    mutationFn: (enabled: boolean) =>
      enabled
        ? api.put(`${cardPath}/watchers/me`)
        : api.delete(`${cardPath}/watchers/me`),
    onSuccess: refresh,
  });
  const download = async (attachment: CardAttachment) => {
    const response = await api.get<Blob>(`${cardPath}/attachments/${attachment.id}`, {
      responseType: "blob",
    });
    const objectUrl = URL.createObjectURL(response.data);
    const anchor = document.createElement("a");
    anchor.href = objectUrl;
    anchor.download = attachment.originalName;
    anchor.click();
    setTimeout(() => URL.revokeObjectURL(objectUrl), 30_000);
  };
  const error =
    comments.error || checklist.error || attachments.error || labels.error ||
    addComment.error || addCheck.error || toggleCheck.error || upload.error ||
    addLabel.error || attachLabel.error || watch.error || task.error || saveTask.error ||
    copyCard.error || transferCard.error || archiveCard.error;
  const isWatching = watchers.data?.includes(user?.id || "") ?? false;
  const done = checklist.data?.filter((item) => item.done).length ?? 0;
  const total = checklist.data?.length ?? 0;
  const shownStartAt = task.data ? task.data.startAt : card.startAt;
  const shownDueAt = task.data ? task.data.dueAt : card.dueAt;

  return (
    <div className="team-dialog-backdrop" role="presentation" onMouseDown={onClose}>
      <div
        className="panel team-card-dialog"
        role="dialog"
        aria-modal="true"
        aria-label={`Chi tiết thẻ ${card.title}`}
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="team-card-dialog-heading">
          <h2>{card.title}</h2>
          <button className="icon-btn" aria-label="Đóng" onClick={onClose} type="button">
            <X size={18} />
          </button>
        </div>
        {canEditTask ? (
          <form onSubmit={(event) => {
            event.preventDefault();
            if (editTitle.trim()) saveTask.mutate();
          }}>
            <input aria-label="Tên thẻ" value={editTitle}
              onChange={(event) => setEditTitle(event.target.value)} maxLength={255} required />
            <textarea aria-label="Mô tả thẻ" value={editDescription}
              onChange={(event) => setEditDescription(event.target.value)} maxLength={10000} rows={2} />
            <label htmlFor="card-start-at">Bắt đầu</label>
            <input id="card-start-at" type="datetime-local" value={startInput}
              max={dueInput || undefined} onChange={(event) => setStartInput(event.target.value)} />
            <label htmlFor="card-due-at">Hạn hoàn thành</label>
            <input id="card-due-at" type="datetime-local" value={dueInput}
              min={startInput || undefined} onChange={(event) => setDueInput(event.target.value)} />
            <button className="secondary" disabled={!editTitle.trim() || saveTask.isPending}>Lưu thẻ</button>
          </form>
        ) : card.description ? <p>{card.description}</p> : null}
        <div className="team-board-card-meta">
          <span>{card.status.replace("_", " ")}</span>
          {shownStartAt && <span>Bắt đầu {new Date(shownStartAt).toLocaleString("vi-VN")}</span>}
          {shownDueAt && <span>Hạn {new Date(shownDueAt).toLocaleString("vi-VN")}</span>}
        </div>
        <section>
          <h3>Thao tác thẻ</h3>
          <div className="team-card-actions">
            <button type="button" className="secondary" onClick={async () => {
              await navigator.clipboard.writeText(`${location.origin}/app/projects/${projectId}/boards/${boardId}?card=${card.id}`);
              setShareFeedback("Đã sao chép liên kết nội bộ");
            }}>Chia sẻ liên kết</button>
            {canEdit && <>
              <select aria-label="Bảng đích" value={targetBoardId} onChange={(event) => {
                setTargetBoardId(event.target.value);
                setTargetListId("");
              }}>
                {boards.data?.filter((value) => value.myRole !== "VIEWER").map((value) =>
                  <option key={value.id} value={value.id}>{value.title}</option>)}
              </select>
              <select aria-label="Danh sách đích" value={targetListId}
                onChange={(event) => setTargetListId(event.target.value)}>
                {targetBoard.data?.lists.map((list) =>
                  <option key={list.id} value={list.id}>{list.title}</option>)}
              </select>
              <button type="button" className="secondary" disabled={!targetListId || copyCard.isPending}
                onClick={() => copyCard.mutate()}>Sao chép</button>
              {canEditTask && <>
                <button type="button" className="secondary" disabled={!targetListId || transferCard.isPending}
                  onClick={() => transferCard.mutate()}>Chuyển bảng</button>
                <button type="button" className="secondary" disabled={archiveCard.isPending}
                  onClick={() => {
                    if (window.confirm(`Lưu trữ thẻ "${card.title}"?`)) archiveCard.mutate();
                  }}>Lưu trữ</button>
              </>}
            </>}
          </div>
          {shareFeedback && <small role="status">{shareFeedback}</small>}
        </section>
        {error && (
          <p role="alert" className="form-error">
            {getApiErrorMessage(error, "Không thể tải hoặc cập nhật thẻ.")}
          </p>
        )}
        <section>
          <h3>Theo dõi</h3>
          <button
            className="secondary"
            type="button"
            onClick={() => watch.mutate(!isWatching)}
          >
            {isWatching ? "Bỏ theo dõi" : "Theo dõi thẻ"}
          </button>
          <small> {watchers.data?.length ?? 0} người theo dõi</small>
        </section>
        <section>
          <h3>Checklist ({done}/{total})</h3>
          <div className="team-card-checklist">
            {checklist.data?.map((item) => (
              <label key={item.id}>
                <input
                  type="checkbox"
                  checked={item.done}
                  disabled={!canEdit || toggleCheck.isPending}
                  onChange={() => toggleCheck.mutate(item)}
                />
                <span>{item.title}</span>
              </label>
            ))}
          </div>
          {canEdit && (
            <form onSubmit={(event: FormEvent) => {
              event.preventDefault();
              if (checkTitle.trim()) addCheck.mutate(checkTitle.trim());
            }}>
              <input
                aria-label="Mục checklist mới"
                value={checkTitle}
                onChange={(event) => setCheckTitle(event.target.value)}
                maxLength={500}
                placeholder="Mục cần hoàn thành"
              />
              <button className="secondary" disabled={!checkTitle.trim()}>Thêm</button>
            </form>
          )}
        </section>
        <section>
          <h3>Nhãn</h3>
          <div className="team-card-labels">
            {labels.data?.map((label) => (
              <span key={label.id} className={`team-card-label ${label.color}`}>{label.name}</span>
            ))}
          </div>
          {canEdit && (
            <>
              <select
                aria-label="Gắn nhãn có sẵn"
                defaultValue=""
                onChange={(event) => {
                  if (event.target.value) attachLabel.mutate(event.target.value);
                  event.target.value = "";
                }}
              >
                <option value="">Gắn nhãn có sẵn</option>
                {boardLabels.data?.filter((label) =>
                  !labels.data?.some((value) => value.id === label.id),
                ).map((label) => (
                  <option value={label.id} key={label.id}>{label.name}</option>
                ))}
              </select>
              <form onSubmit={(event: FormEvent) => {
                event.preventDefault();
                if (labelName.trim()) addLabel.mutate(labelName.trim());
              }}>
                <input
                  aria-label="Tên nhãn mới"
                  value={labelName}
                  onChange={(event) => setLabelName(event.target.value)}
                  maxLength={80}
                  placeholder="Tên nhãn mới"
                />
                <button className="secondary" disabled={!labelName.trim()}>Tạo nhãn</button>
              </form>
            </>
          )}
        </section>
        <section>
          <h3><Paperclip size={17} /> Tệp đính kèm</h3>
          <div className="team-card-attachments">
            {attachments.data?.map((item) => (
              <button className="secondary" type="button" key={item.id} onClick={() => download(item)}>
                {item.originalName} ({Math.ceil(item.byteSize / 1024)} KB)
              </button>
            ))}
          </div>
          {canEdit && (
            <form onSubmit={(event) => {
              event.preventDefault();
              if (file) upload.mutate(file);
            }}>
              <input
                type="file"
                aria-label="Chọn tệp đính kèm"
                onChange={(event) => setFile(event.target.files?.[0])}
              />
              <button className="secondary" disabled={!file || upload.isPending}>Tải lên</button>
            </form>
          )}
        </section>
        <section>
          <h3>Bình luận</h3>
          <div className="team-card-comments">
            {comments.data?.map((item) => (
              <div key={item.id}>
                <small>{item.authorId === user?.id ? "Bạn" : item.authorId.slice(0, 8)}
                  {" · "}{new Date(item.createdAt).toLocaleString("vi-VN")}</small>
                <p>{item.body}</p>
              </div>
            ))}
          </div>
          {canEdit && (
            <form onSubmit={(event) => {
              event.preventDefault();
              if (commentText.trim()) addComment.mutate(commentText.trim());
            }}>
              <textarea
                aria-label="Nội dung bình luận"
                value={commentText}
                onChange={(event) => setCommentText(event.target.value)}
                maxLength={5000}
                rows={2}
                placeholder="Viết bình luận..."
              />
              <button className="primary" disabled={!commentText.trim() || addComment.isPending}>
                Gửi bình luận
              </button>
            </form>
          )}
        </section>
        <section>
          <h3>Hoạt động của thẻ</h3>
          {events.data?.length === 0 && <p>Chưa có hoạt động.</p>}
          {[...(events.data || [])].reverse().map((event) => (
            <div key={event.id} className="team-card-event">
              {event.kind.replaceAll("_", " ")} · {new Date(event.createdAt).toLocaleString("vi-VN")}
            </div>
          ))}
        </section>
      </div>
    </div>
  );
}
