import { useEffect, useState, type FormEvent } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import {
  DndContext,
  PointerSensor,
  useDraggable,
  useDroppable,
  useSensor,
  useSensors,
  type DragEndEvent,
} from "@dnd-kit/core";
import { CSS } from "@dnd-kit/utilities";
import { ArrowLeft, GripVertical, Plus } from "lucide-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, getApiErrorMessage } from "../api";
import BoardChat from "../components/BoardChat";
import CardDetailPanel from "../components/CardDetailPanel";
import type { BoardCard, BoardEvent, BoardList, ProjectBoardDetails } from "../types";

function Card({
  card,
  canEdit,
  onMove,
  onOpen,
  index,
  count,
}: {
  card: BoardCard;
  canEdit: boolean;
  onMove: (card: BoardCard, index: number) => void;
  onOpen: (card: BoardCard) => void;
  index: number;
  count: number;
}) {
  const { attributes, listeners, setNodeRef, transform, isDragging } =
    useDraggable({ id: card.id, disabled: !canEdit });
  return (
    <article
      ref={setNodeRef}
      className="team-board-card"
      style={{
        transform: CSS.Translate.toString(transform),
        opacity: isDragging ? 0.55 : 1,
      }}
    >
      <div className="team-board-card-title">
        {canEdit && (
          <button
            type="button"
            className="drag-handle"
            aria-label={`Kéo thẻ ${card.title}`}
            {...listeners}
            {...attributes}
          >
            <GripVertical size={15} />
          </button>
        )}
        <button type="button" className="team-board-card-open" onClick={() => onOpen(card)}>
          {card.title}
        </button>
      </div>
      {card.description && <p>{card.description}</p>}
      <div className="team-board-card-meta">
        <span>{card.status.replace("_", " ")}</span>
        {card.dueAt && (
          <span>Hạn {new Date(card.dueAt).toLocaleDateString("vi-VN")}</span>
        )}
      </div>
      {canEdit && (
        <div className="team-board-card-order">
          <button
            type="button"
            className="secondary"
            disabled={index === 0}
            onClick={() => onMove(card, index - 1)}
          >
            ↑
          </button>
          <button
            type="button"
            className="secondary"
            disabled={index + 1 >= count}
            onClick={() => onMove(card, index + 1)}
          >
            ↓
          </button>
        </div>
      )}
    </article>
  );
}

function Column({
  list,
  canEdit,
  onCreate,
  onMove,
  onOpen,
  onRename,
  onDelete,
  onMoveList,
  listIndex,
  listCount,
}: {
  list: BoardList;
  canEdit: boolean;
  onCreate: (listId: string, title: string) => void;
  onMove: (card: BoardCard, listId: string, index: number) => void;
  onOpen: (card: BoardCard) => void;
  onRename: (list: BoardList) => void;
  onDelete: (list: BoardList) => void;
  onMoveList: (list: BoardList, offset: number) => void;
  listIndex: number;
  listCount: number;
}) {
  const { setNodeRef, isOver } = useDroppable({ id: list.id });
  const [title, setTitle] = useState("");
  return (
    <section
      ref={setNodeRef}
      className={`team-board-column panel ${isOver ? "over" : ""}`}
    >
      <header>
        <h2>{list.title}</h2>
        <span>{list.cards.length}</span>
      </header>
      {canEdit && (
        <div className="team-board-list-actions">
          <button className="secondary" type="button" disabled={listIndex === 0}
            onClick={() => onMoveList(list, -1)} aria-label={`Đưa ${list.title} sang trái`}>←</button>
          <button className="secondary" type="button" disabled={listIndex + 1 >= listCount}
            onClick={() => onMoveList(list, 1)} aria-label={`Đưa ${list.title} sang phải`}>→</button>
          <button className="secondary" type="button" onClick={() => onRename(list)}>Đổi tên</button>
          {!list.workflowStatus && list.cards.length === 0 &&
            <button className="secondary" type="button" onClick={() => onDelete(list)}>Xóa</button>}
        </div>
      )}
      <div className="team-board-stack">
        {list.cards.map((card, index) => (
          <Card
            key={card.id}
            card={card}
            canEdit={canEdit}
            index={index}
            count={list.cards.length}
            onMove={(value, targetIndex) => onMove(value, list.id, targetIndex)}
            onOpen={onOpen}
          />
        ))}
        {!list.cards.length && <div className="column-empty">Kéo thẻ vào đây</div>}
      </div>
      {canEdit && (
        <form
          className="team-board-add-card"
          onSubmit={(event: FormEvent) => {
            event.preventDefault();
            if (title.trim()) {
              onCreate(list.id, title.trim());
              setTitle("");
            }
          }}
        >
          <input
            aria-label={`Tên thẻ mới trong ${list.title}`}
            value={title}
            onChange={(event) => setTitle(event.target.value)}
            maxLength={255}
            placeholder="Tên thẻ mới"
          />
          <button className="secondary" disabled={!title.trim()}>
            <Plus size={15} /> Thêm
          </button>
        </form>
      )}
    </section>
  );
}

export default function ProjectBoardPage() {
  const { projectId = "", boardId = "" } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [newList, setNewList] = useState("");
  const [boardTitle, setBoardTitle] = useState("");
  const [boardDescription, setBoardDescription] = useState("");
  const [copyBoardTitle, setCopyBoardTitle] = useState("");
  const [selected, setSelected] = useState<BoardCard>();
  const [view, setView] = useState<"board" | "table" | "calendar" | "dashboard">("board");
  const [search, setSearch] = useState("");
  const [month, setMonth] = useState(() => {
    const today = new Date();
    return `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}`;
  });
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 8 } }),
  );
  const key = ["project-board", projectId, boardId] as const;
  useEffect(() => {
    if (projectId && boardId) {
      api.post(`/projects/${projectId}/boards/${boardId}/view`)
        .then(() => queryClient.invalidateQueries({ queryKey: ["recent-boards"] }))
        .catch(() => {});
    }
  }, [projectId, boardId, queryClient]);
  const {
    data,
    isLoading,
    error: loadError,
  } = useQuery({
    queryKey: key,
    queryFn: async () =>
      (
        await api.get<ProjectBoardDetails>(
          `/projects/${projectId}/boards/${boardId}`,
        )
      ).data,
    enabled: !!projectId && !!boardId,
    refetchInterval: 3000,
  });
  useEffect(() => {
    const cardId = new URLSearchParams(window.location.search).get("card");
    if (cardId && !selected && data) {
      const card = data.lists.flatMap((list) => list.cards).find((value) => value.id === cardId);
      if (card) setSelected(card);
    }
  }, [data, selected]);
  useEffect(() => {
    if (data?.board) {
      setBoardTitle(data.board.title);
      setBoardDescription(data.board.description || "");
      setCopyBoardTitle(`${data.board.title} (bản sao)`);
    }
  }, [data?.board.id, data?.board.title, data?.board.description]);
  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: key });
    queryClient.invalidateQueries({ queryKey: ["projects", projectId] });
  };
  const { data: events = [] } = useQuery({
    queryKey: ["board-events", projectId, boardId],
    queryFn: async () =>
      (await api.get<BoardEvent[]>(`/projects/${projectId}/boards/${boardId}/events`)).data,
    enabled: !!projectId && !!boardId,
    refetchInterval: 3000,
  });
  const { data: archivedCards = [] } = useQuery({
    queryKey: ["archived-cards", projectId, boardId],
    queryFn: async () => (await api.get<BoardCard[]>(
      `/projects/${projectId}/boards/${boardId}/archived-cards`)).data,
    enabled: !!projectId && !!boardId,
  });
  const restoreCard = useMutation({
    mutationFn: (cardId: string) => api.post(
      `/projects/${projectId}/boards/${boardId}/archived-cards/${cardId}/restore`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["archived-cards", projectId, boardId] });
      refresh();
    },
  });
  const createList = useMutation({
    mutationFn: (title: string) =>
      api.post(`/projects/${projectId}/boards/${boardId}/lists`, { title }),
    onSuccess: () => {
      setNewList("");
      refresh();
    },
  });
  const updateBoard = useMutation({
    mutationFn: () => api.put(`/projects/${projectId}/boards/${boardId}`, {
      title: boardTitle.trim(), description: boardDescription.trim() || null,
      visibility: "WORKSPACE", background: data?.board.background || "indigo",
    }),
    onSuccess: refresh,
  });
  const copyBoard = useMutation({
    mutationFn: async () => (await api.post<{ id: string }>(
      `/projects/${projectId}/boards/${boardId}/copy`,
      { title: copyBoardTitle.trim() },
    )).data,
    onSuccess: (copied) => {
      queryClient.invalidateQueries({ queryKey: ["projects", projectId, "boards"] });
      setView("board");
      navigate(`/app/projects/${projectId}/boards/${copied.id}`);
    },
  });
  const renameList = useMutation({
    mutationFn: ({ listId, title }: { listId: string; title: string }) =>
      api.put(`/projects/${projectId}/boards/${boardId}/lists/${listId}`, { title }),
    onSuccess: refresh,
  });
  const deleteList = useMutation({
    mutationFn: (listId: string) => api.delete(`/projects/${projectId}/boards/${boardId}/lists/${listId}`),
    onSuccess: refresh,
  });
  const reorderLists = useMutation({
    mutationFn: (listIds: string[]) => api.put(`/projects/${projectId}/boards/${boardId}/lists/order`, { listIds }),
    onSuccess: refresh,
  });
  const createCard = useMutation({
    mutationFn: ({ listId, title }: { listId: string; title: string }) =>
      api.post(
        `/projects/${projectId}/boards/${boardId}/lists/${listId}/cards`,
        { title },
      ),
    onSuccess: refresh,
  });
  const moveCard = useMutation({
    mutationFn: ({
      card,
      listId,
      index,
    }: {
      card: BoardCard;
      listId: string;
      index: number;
    }) =>
      api.patch(
        `/projects/${projectId}/boards/${boardId}/cards/${card.id}/move`,
        { listId, index, version: card.version },
      ),
    onSuccess: refresh,
    onError: refresh,
  });
  const canEdit = data?.board.myRole !== "VIEWER";
  const onDragEnd = ({ active, over }: DragEndEvent) => {
    if (!over || !data || !canEdit || search) return;
    const card = data.lists.flatMap((list) => list.cards).find((item) => item.id === active.id);
    const target = data.lists.find((list) => list.id === over.id);
    if (!card || !target) return;
    const source = data.lists.find((list) => list.cards.some((item) => item.id === card.id));
    if (source?.id === target.id) return;
    moveCard.mutate({ card, listId: target.id, index: target.cards.length });
  };
  const operationError = createList.error || createCard.error || moveCard.error ||
    updateBoard.error || copyBoard.error || renameList.error || deleteList.error ||
    reorderLists.error || restoreCard.error;
  const filteredLists = data?.lists.map((list) => ({
    ...list,
    cards: list.cards.filter((card) =>
      `${card.title} ${card.description || ""}`.toLocaleLowerCase("vi-VN")
        .includes(search.toLocaleLowerCase("vi-VN"))),
  })) || [];
  const allCards = filteredLists.flatMap((list) =>
    list.cards.map((card) => ({ card, list: list.title })));
  const calendarDate = new Date(`${month}-01T00:00:00`);
  const calendarDays = Number.isNaN(calendarDate.getTime()) ? 0 :
    new Date(calendarDate.getFullYear(), calendarDate.getMonth() + 1, 0).getDate();

  return (
    <div className="team-page">
      <Link className="team-back" to={`/app/projects/${projectId}`}>
        <ArrowLeft size={17} /> Dự án nhóm
      </Link>
      {isLoading && <p>Đang tải bảng...</p>}
      {loadError && (
        <p className="form-error" role="alert">
          {getApiErrorMessage(loadError, "Không thể tải bảng.")}
        </p>
      )}
      {data && (
        <>
          <div className="page-heading">
            <div>
              <span className="eyebrow">BẢNG CÔNG VIỆC</span>
              <h1>{data.board.title}</h1>
              <p>{data.board.description || "Cùng nhóm theo dõi tiến độ trên từng thẻ."}</p>
            </div>
          </div>
          {data.board.myRole === "ADMIN" && (
            <details className="panel team-board-settings">
              <summary>Cài đặt bảng</summary>
              <form onSubmit={(event) => {
                event.preventDefault();
                if (boardTitle.trim()) updateBoard.mutate();
              }}>
                <input aria-label="Tên bảng" value={boardTitle}
                  onChange={(event) => setBoardTitle(event.target.value)} maxLength={160} required />
                <textarea aria-label="Mô tả bảng" value={boardDescription}
                  onChange={(event) => setBoardDescription(event.target.value)} maxLength={5000} rows={2} />
                <button className="secondary" disabled={!boardTitle.trim() || updateBoard.isPending}>Lưu bảng</button>
              </form>
              <form onSubmit={(event) => {
                event.preventDefault();
                if (copyBoardTitle.trim()) copyBoard.mutate();
              }}>
                <label htmlFor="copy-board-title">Sao chép bảng</label>
                <input id="copy-board-title" value={copyBoardTitle} maxLength={160} required
                  onChange={(event) => setCopyBoardTitle(event.target.value)} />
                <p>Cột, thẻ, checklist và nhãn được sao chép. Bình luận, tệp và chat không được sao chép.</p>
                <button className="secondary" disabled={!copyBoardTitle.trim() || copyBoard.isPending}>
                  {copyBoard.isPending ? "Đang sao chép..." : "Tạo bản sao"}
                </button>
              </form>
            </details>
          )}
          {operationError && (
            <p className="form-error" role="alert">
              {getApiErrorMessage(operationError, "Không thể thực hiện thao tác.")}
            </p>
          )}
          <div className="team-board-toolbar">
            <div role="group" aria-label="Chế độ xem bảng">
              {(["board", "table", "calendar", "dashboard"] as const).map((item) => (
                <button key={item} type="button" className={view === item ? "primary" : "secondary"}
                  onClick={() => setView(item)}>
                  {{ board: "Bảng", table: "Bảng dữ liệu", calendar: "Lịch", dashboard: "Thống kê" }[item]}
                </button>
              ))}
            </div>
            <input aria-label="Tìm thẻ" value={search} onChange={(event) => setSearch(event.target.value)}
              placeholder="Tìm theo tên hoặc mô tả..." />
          </div>
          {view === "board" && <DndContext sensors={sensors} onDragEnd={onDragEnd}>
            <div className="team-board-columns">
              {filteredLists.map((list, index) => (
                <Column
                  key={list.id}
                  list={list}
                  canEdit={canEdit && !search}
                  onCreate={(listId, title) => createCard.mutate({ listId, title })}
                  onMove={(card, listId, index) =>
                    moveCard.mutate({ card, listId, index })
                  }
                  onOpen={setSelected}
                  listIndex={index}
                  listCount={filteredLists.length}
                  onRename={(value) => {
                    const title = window.prompt("Tên danh sách mới", value.title)?.trim();
                    if (title) renameList.mutate({ listId: value.id, title });
                  }}
                  onDelete={(value) => {
                    if (window.confirm(`Xóa danh sách trống "${value.title}"?`)) deleteList.mutate(value.id);
                  }}
                  onMoveList={(value, offset) => {
                    const ids = data.lists.map((item) => item.id);
                    const at = ids.indexOf(value.id);
                    const target = at + offset;
                    if (at >= 0 && target >= 0 && target < ids.length) {
                      [ids[at], ids[target]] = [ids[target], ids[at]];
                      reorderLists.mutate(ids);
                    }
                  }}
                />
              ))}
              {canEdit && (
                <form
                  className="team-board-column panel team-board-new-list"
                  onSubmit={(event) => {
                    event.preventDefault();
                    if (newList.trim()) createList.mutate(newList.trim());
                  }}
                >
                  <h2>Thêm danh sách</h2>
                  <input
                    aria-label="Tên danh sách mới"
                    value={newList}
                    onChange={(event) => setNewList(event.target.value)}
                    maxLength={160}
                    placeholder="Tên danh sách"
                  />
                  <button className="secondary" disabled={!newList.trim() || createList.isPending}>
                    <Plus size={15} /> Thêm danh sách
                  </button>
                </form>
              )}
            </div>
          </DndContext>}
          {view === "table" && (
            <div className="panel team-board-table-wrap">
              <table className="team-board-table">
                <thead><tr><th>Thẻ</th><th>Danh sách</th><th>Trạng thái</th><th>Hạn</th></tr></thead>
                <tbody>{allCards.map(({ card, list }) => (
                  <tr key={card.id}>
                    <td><button type="button" onClick={() => setSelected(card)}>{card.title}</button></td>
                    <td>{list}</td><td>{card.status}</td>
                    <td>{card.dueAt ? new Date(card.dueAt).toLocaleDateString("vi-VN") : "—"}</td>
                  </tr>
                ))}</tbody>
              </table>
            </div>
          )}
          {view === "calendar" && (
            <section className="panel team-board-calendar">
              <label>Tháng <input type="month" value={month} onChange={(event) => setMonth(event.target.value)} /></label>
              <div className="team-board-calendar-grid">
                {Array.from({ length: calendarDays }, (_, index) => {
                  const day = index + 1;
                  const cards = allCards.filter(({ card }) => card.dueAt &&
                    new Date(card.dueAt).getFullYear() === calendarDate.getFullYear() &&
                    new Date(card.dueAt).getMonth() === calendarDate.getMonth() &&
                    new Date(card.dueAt).getDate() === day);
                  return <div key={day}><strong>{day}</strong>{cards.map(({ card }) =>
                    <button key={card.id} type="button" onClick={() => setSelected(card)}>{card.title}</button>)}</div>;
                })}
              </div>
            </section>
          )}
          {view === "dashboard" && (
            <section className="team-stats">
              <div className="panel"><strong>{allCards.length}</strong><span>Tổng thẻ</span></div>
              <div className="panel"><strong>{allCards.filter(({ card }) => card.status === "COMPLETED").length}</strong><span>Hoàn thành</span></div>
              <div className="panel"><strong>{allCards.filter(({ card }) => card.dueAt &&
                new Date(card.dueAt) < new Date() && card.status !== "COMPLETED").length}</strong><span>Quá hạn</span></div>
              <div className="panel"><strong>{allCards.length ? Math.round(allCards.filter(({ card }) =>
                card.status === "COMPLETED").length * 100 / allCards.length) : 0}%</strong><span>Tiến độ</span></div>
            </section>
          )}
          <section className="panel team-board-activity">
            <h2>Hoạt động gần đây</h2>
            {events.length === 0 && <p>Chưa có hoạt động.</p>}
            {[...events].reverse().slice(0, 20).map((event) => (
              <div key={event.id}>
                <span>{event.kind.replaceAll("_", " ")}</span>
                <small>{new Date(event.createdAt).toLocaleString("vi-VN")}</small>
              </div>
            ))}
          </section>
          {archivedCards.length > 0 && (
            <details className="panel team-board-archive">
              <summary>Thẻ đã lưu trữ ({archivedCards.length})</summary>
              {archivedCards.map((card) => (
                <div key={card.id}>
                  <span>{card.title}</span>
                  {data.board.myRole === "ADMIN" &&
                    <button className="secondary" type="button" onClick={() => restoreCard.mutate(card.id)}>
                      Khôi phục
                    </button>}
                </div>
              ))}
            </details>
          )}
          <BoardChat projectId={projectId} boardId={boardId} canWrite={canEdit} />
          {selected && (
            <CardDetailPanel
              projectId={projectId}
              boardId={boardId}
              card={data.lists.flatMap((list) => list.cards).find((card) => card.id === selected.id) || selected}
              canEdit={canEdit}
              canManageTasks={data.board.myRole === "ADMIN"}
              listId={data.lists.find((list) => list.cards.some((card) => card.id === selected.id))?.id || ""}
              onClose={() => {
                setSelected(undefined);
                if (window.location.search) window.history.replaceState({}, "", window.location.pathname);
              }}
            />
          )}
        </>
      )}
    </div>
  );
}
