import { useEffect, useState } from "react";
import { NavLink, useLocation, useNavigate, useParams } from "react-router-dom";
import { Filter, Plus, Search, SlidersHorizontal, X } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { api, getApiErrorMessage } from "../api";
import { useAuth } from "../auth";
import type { Category, Priority, Status, Tag, Task, TaskPage } from "../types";
import TaskCard from "../components/TaskCard";
import TaskForm from "../components/TaskForm";

const titles: Record<string, [string, string]> = {
  today: ["Hôm nay", "Những việc cần sự chú ý trong ngày."],
  upcoming: ["Sắp đến hạn", "Chuẩn bị trước cho bảy ngày tới."],
  overdue: ["Đã quá hạn", "Xem lại và sắp xếp các công việc bị trễ."],
};

export default function TasksPage() {
  const { view } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const { user } = useAuth();
  const [open, setOpen] = useState(false);
  const [edit, setEdit] = useState<Task>();
  const [search, setSearch] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [status, setStatus] = useState<Status | "">("");
  const [priority, setPriority] = useState<Priority | "">("");
  const [categoryId, setCategoryId] = useState("");
  const [tagId, setTagId] = useState("");
  const [filtersOpen, setFiltersOpen] = useState(false);

  useEffect(() => {
    const parameters = new URLSearchParams(location.search);
    const query = parameters.get("q") || "";
    setSearch(query);
    setDebouncedSearch(query);
    if (parameters.get("new")) {
      setOpen(true);
      parameters.delete("new");
      navigate(
        {
          pathname: location.pathname,
          search: parameters.toString() ? `?${parameters.toString()}` : "",
        },
        { replace: true },
      );
    }
  }, [location.pathname, location.search, navigate]);
  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedSearch(search), 300);
    return () => window.clearTimeout(timer);
  }, [search]);

  const endpoint =
    view === "today"
      ? "/tasks/today"
      : view === "upcoming"
        ? "/tasks/upcoming"
        : view === "overdue"
          ? "/tasks/overdue"
          : "/tasks";

  const { data: categories = [] } = useQuery({
    queryKey: ["categories"],
    queryFn: async () => (await api.get<Category[]>("/categories")).data,
  });
  const { data: tags = [] } = useQuery({
    queryKey: ["tags"],
    queryFn: async () => (await api.get<Tag[]>("/tags")).data,
  });
  const {
    data = [],
    isLoading,
    isError,
    error,
  } = useQuery({
    queryKey: [
      "tasks",
      view,
      debouncedSearch,
      status,
      priority,
      categoryId,
      tagId,
      user?.timezone,
    ],
    queryFn: async () => {
      const serverParams =
        endpoint === "/tasks"
          ? {
              keyword: debouncedSearch || undefined,
              status: status || undefined,
              priority: priority || undefined,
              categoryId: categoryId || undefined,
              tagId: tagId || undefined,
              size: 100,
            }
          : endpoint === "/tasks/overdue"
            ? undefined
            : { timezone: user?.timezone || "Asia/Bangkok" };
      const response = await api.get(endpoint, { params: serverParams });
      const values = (
        Array.isArray(response.data)
          ? response.data
          : (response.data as TaskPage).content
      ) as Task[];
      if (endpoint === "/tasks") return values;
      const normalizedSearch = debouncedSearch.trim().toLocaleLowerCase("vi");
      return values.filter(
        (task) =>
          (!normalizedSearch ||
            task.title.toLocaleLowerCase("vi").includes(normalizedSearch)) &&
          (!status || task.status === status) &&
          (!priority || task.priority === priority) &&
          (!categoryId || task.categoryId === categoryId) &&
          (!tagId || task.tags.some((tag) => tag.id === tagId)),
      );
    },
  });

  const title = titles[view || ""] || [
    "Tất cả công việc",
    "Một nơi duy nhất cho mọi điều bạn cần hoàn thành.",
  ];
  const hasAdvancedFilters = !!(priority || categoryId || tagId);
  const hasAnyFilter = !!(search || status || priority || categoryId || tagId);
  const resetFilters = () => {
    setSearch("");
    setDebouncedSearch("");
    setStatus("");
    setPriority("");
    setCategoryId("");
    setTagId("");
  };

  return (
    <>
      <div className="page-heading">
        <div>
          <span className="eyebrow">KHÔNG GIAN CÔNG VIỆC</span>
          <h1>{title[0]}</h1>
          <p>{title[1]}</p>
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
      <section className="panel tasks-panel">
        <div className="task-view-tabs">
          <NavLink to="/app/tasks" end>
            Tất cả
          </NavLink>
          <NavLink to="/app/tasks/today">Hôm nay</NavLink>
          <NavLink to="/app/tasks/upcoming">Sắp đến hạn</NavLink>
          <NavLink to="/app/tasks/overdue">Quá hạn</NavLink>
        </div>
        <div className="filterbar">
          <div className="search-box">
            <Search size={18} />
            <input
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Tìm theo tiêu đề..."
              aria-label="Tìm theo tiêu đề"
            />
          </div>
          <select
            value={status}
            onChange={(event) => setStatus(event.target.value as Status | "")}
            aria-label="Lọc theo trạng thái"
          >
            <option value="">Mọi trạng thái</option>
            <option value="TODO">Cần làm</option>
            <option value="IN_PROGRESS">Đang thực hiện</option>
            <option value="COMPLETED">Hoàn thành</option>
            <option value="CANCELLED">Đã hủy</option>
          </select>
          <button
            className={hasAdvancedFilters ? "ghost active-filter" : "ghost"}
            onClick={() => setFiltersOpen((value) => !value)}
            aria-expanded={filtersOpen}
          >
            <SlidersHorizontal size={17} />
            Bộ lọc
          </button>
        </div>
        {filtersOpen && (
          <div className="advanced-filters">
            <label>
              Ưu tiên
              <select
                value={priority}
                onChange={(event) =>
                  setPriority(event.target.value as Priority | "")
                }
              >
                <option value="">Tất cả</option>
                <option value="LOW">Thấp</option>
                <option value="MEDIUM">Trung bình</option>
                <option value="HIGH">Cao</option>
                <option value="URGENT">Khẩn cấp</option>
              </select>
            </label>
            <label>
              Danh mục
              <select
                value={categoryId}
                onChange={(event) => setCategoryId(event.target.value)}
              >
                <option value="">Tất cả</option>
                {categories.map((category) => (
                  <option key={category.id} value={category.id}>
                    {category.name}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Nhãn
              <select
                value={tagId}
                onChange={(event) => setTagId(event.target.value)}
              >
                <option value="">Tất cả</option>
                {tags.map((tag) => (
                  <option key={tag.id} value={tag.id}>
                    {tag.name}
                  </option>
                ))}
              </select>
            </label>
            <button className="text-btn" onClick={resetFilters}>
              <X size={15} /> Xóa bộ lọc
            </button>
          </div>
        )}
        {isLoading ? (
          <div className="loading-list">Đang tải công việc...</div>
        ) : isError ? (
          <div className="form-error" role="alert">
            {getApiErrorMessage(error, "Không thể tải danh sách công việc.")}
          </div>
        ) : data.length ? (
          <div className="task-list wide">
            {data.map((task) => (
              <TaskCard
                key={task.id}
                task={task}
                onClick={() => {
                  setEdit(task);
                  setOpen(true);
                }}
              />
            ))}
          </div>
        ) : (
          <div className="empty-state">
            <Filter />
            <h3>
              {hasAnyFilter
                ? "Không tìm thấy công việc phù hợp"
                : "Bạn chưa có công việc nào"}
            </h3>
            <p>
              {hasAnyFilter
                ? "Xóa bộ lọc để xem lại toàn bộ công việc."
                : "Tạo công việc đầu tiên để bắt đầu kế hoạch của bạn."}
            </p>
            {hasAnyFilter && (
              <button className="ghost small" onClick={resetFilters}>
                <X size={15} /> Xóa bộ lọc
              </button>
            )}
          </div>
        )}
      </section>
      <TaskForm
        open={open}
        task={edit}
        onSaved={(created) => {
          if (created) {
            resetFilters();
            navigate("/app/tasks", { replace: true });
          }
        }}
        onClose={() => {
          setOpen(false);
          setEdit(undefined);
        }}
      />
    </>
  );
}
