import { useEffect, useRef, useState, type FormEvent } from "react";
import { NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import {
  Bot,
  CalendarDays,
  CheckSquare2,
  ChevronLeft,
  ChevronRight,
  LayoutDashboard,
  LogOut,
  Menu,
  Settings,
  Target,
  UsersRound,
  Workflow,
  Bell,
  Search,
  Plus,
  Tags,
  ShieldCheck,
  CheckCircle2,
  CircleAlert,
  X,
} from "lucide-react";
import { useAuth } from "../auth";
import { api } from "../api";
import { useQuery } from "@tanstack/react-query";
import { applyTheme, type ThemePreference } from "../theme";
import type { FeedbackKind } from "../feedback";
const links = [
  ["/app/dashboard", "Tổng quan", LayoutDashboard],
  ["/app/tasks/today", "Hôm nay", CheckSquare2],
  ["/app/tasks", "Công việc", CheckSquare2],
  ["/app/board", "Bảng Kanban", Workflow],
  ["/app/calendar", "Lịch", CalendarDays],
  ["/app/goals", "Mục tiêu", Target],
  ["/app/projects", "Dự án nhóm", UsersRound],
  ["/app/organize", "Phân loại", Tags],
  ["/app/ai-assistant", "Trợ lý AI", Bot],
  ["/app/notifications", "Thông báo", Bell],
  ["/app/settings", "Cài đặt", Settings],
] as const;
export default function Layout() {
  const [collapsed, setCollapsed] = useState(false);
  const [mobile, setMobile] = useState(false);
  const [search, setSearch] = useState("");
  const [feedback, setFeedback] = useState<{
    message: string;
    kind: FeedbackKind;
  }>();
  const searchRef = useRef<HTMLInputElement>(null);
  const { user, logout } = useAuth();
  const location = useLocation();
  const nav = useNavigate();
  const { data } = useQuery({
    queryKey: ["unread"],
    queryFn: async () => {
      const r = await api.get("/notifications/unread-count");
      return r.data as { unreadCount: number };
    },
    refetchInterval: 30000,
  });
  const { data: settings } = useQuery({
    queryKey: ["settings"],
    queryFn: async () =>
      (await api.get<{ theme: ThemePreference }>("/users/me/settings")).data,
  });
  useEffect(() => {
    if (settings?.theme) applyTheme(settings.theme);
  }, [settings?.theme]);
  useEffect(() => {
    const handleFeedback = (event: Event) => {
      const detail = (
        event as CustomEvent<{ message: string; kind: FeedbackKind }>
      ).detail;
      setFeedback(detail);
    };
    window.addEventListener("flowmate:feedback", handleFeedback);
    return () =>
      window.removeEventListener("flowmate:feedback", handleFeedback);
  }, []);
  useEffect(() => {
    if (!feedback) return;
    const timer = window.setTimeout(() => setFeedback(undefined), 3500);
    return () => window.clearTimeout(timer);
  }, [feedback]);
  useEffect(() => {
    const handleShortcut = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement;
      const isEditing =
        ["INPUT", "TEXTAREA", "SELECT"].includes(target.tagName) ||
        target.isContentEditable;
      if (event.key === "Escape" && mobile) {
        setMobile(false);
        return;
      }
      if (
        isEditing ||
        document.querySelector('[role="dialog"]') ||
        event.ctrlKey ||
        event.metaKey ||
        event.altKey
      )
        return;
      if (event.key === "/") {
        event.preventDefault();
        searchRef.current?.focus();
      } else if (event.key.toLowerCase() === "q") {
        event.preventDefault();
        nav("/app/tasks?new=true");
      }
    };
    document.addEventListener("keydown", handleShortcut);
    return () => document.removeEventListener("keydown", handleShortcut);
  }, [mobile, nav]);
  const leave = async () => {
    const refreshToken = localStorage.getItem("refreshToken");
    try {
      if (refreshToken) {
        await api.post("/auth/logout", { refreshToken });
      }
    } finally {
      logout();
      nav("/login");
    }
  };
  const openSearch = () => {
    const keyword = search.trim();
    nav(keyword ? `/app/tasks?q=${encodeURIComponent(keyword)}` : "/app/tasks");
  };
  const submitSearch = (event: FormEvent) => {
    event.preventDefault();
    openSearch();
  };
  const pageHasCreateAction =
    location.pathname.startsWith("/app/tasks") ||
    location.pathname === "/app/calendar";
  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">
        Đi đến nội dung chính
      </a>
      {mobile && (
        <button
          className="sidebar-backdrop"
          onClick={() => setMobile(false)}
          aria-label="Đóng menu điều hướng"
        />
      )}
      <aside
        className={`sidebar ${collapsed ? "collapsed" : ""} ${mobile ? "mobile-open" : ""}`}
      >
        <div className="brand">
          <div className="brand-mark">F</div>
          {!collapsed && (
            <div>
              <strong>Flowmate</strong>
              <span>Personal &amp; team tasks</span>
            </div>
          )}
        </div>
        <nav>
          {links.map(([to, label, Icon]) => (
            <NavLink
              key={to}
              to={to}
              onClick={() => setMobile(false)}
              className={() => {
                const isActive =
                  to === "/app/tasks"
                    ? location.pathname.startsWith("/app/tasks") &&
                      location.pathname !== "/app/tasks/today"
                    : to === "/app/projects"
                      ? location.pathname.startsWith(to)
                      : location.pathname === to;
                return isActive ? "active" : "";
              }}
            >
              <Icon size={19} />
              {!collapsed && <span>{label}</span>}
              {label === "Thông báo" && !!data?.unreadCount && (
                <b className="nav-badge">{data.unreadCount}</b>
              )}
            </NavLink>
          ))}
          {user?.role === "ADMIN" && (
            <NavLink
              to="/admin/dashboard"
              onClick={() => setMobile(false)}
              className={({ isActive }) => (isActive ? "active" : "")}
            >
              <ShieldCheck size={19} />
              {!collapsed && <span>Quản trị hệ thống</span>}
            </NavLink>
          )}
        </nav>
        <div className="sidebar-footer">
          <div className="sidebar-user">
            <span className="avatar">
              {user?.fullName?.charAt(0).toUpperCase()}
            </span>
            {!collapsed && (
              <span>
                <strong>{user?.fullName}</strong>
                <small>{user?.email}</small>
              </span>
            )}
            <button
              className="sidebar-logout"
              onClick={leave}
              aria-label="Đăng xuất"
              title="Đăng xuất"
            >
              <LogOut size={17} />
            </button>
          </div>
          <button
            className="collapse-btn"
            onClick={() => setCollapsed(!collapsed)}
            aria-label={
              collapsed
                ? "Mở rộng thanh điều hướng"
                : "Thu gọn thanh điều hướng"
            }
          >
            {collapsed ? <ChevronRight /> : <ChevronLeft />}
          </button>
        </div>
      </aside>
      <main className="main">
        <header className="topbar">
          <button
            className="icon-btn mobile-menu"
            onClick={() => setMobile(!mobile)}
            aria-label={mobile ? "Đóng menu" : "Mở menu"}
          >
            <Menu />
          </button>
          <form className="global-search" onSubmit={submitSearch}>
            <Search size={18} />
            <input
              ref={searchRef}
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter") {
                  event.preventDefault();
                  openSearch();
                }
              }}
              placeholder="Tìm nhanh công việc..."
              aria-label="Tìm nhanh công việc"
            />
            <button type="submit" aria-label="Tìm kiếm công việc">
              Tìm
            </button>
          </form>
          <div className="top-actions">
            {!pageHasCreateAction && (
              <button
                className="primary small"
                onClick={() => nav("/app/tasks?new=true")}
                title="Tạo công việc (Q)"
              >
                <Plus size={17} />
                Tạo công việc
              </button>
            )}
            <button
              className="icon-btn"
              onClick={() => nav("/app/notifications")}
              aria-label="Mở thông báo"
            >
              <Bell size={19} />
              {!!data?.unreadCount && <i />}
            </button>
          </div>
        </header>
        <div className="page" id="main-content" tabIndex={-1}>
          <Outlet />
        </div>
      </main>
      {feedback && (
        <div
          className={`global-feedback ${feedback.kind}`}
          role={feedback.kind === "error" ? "alert" : "status"}
          aria-live="polite"
        >
          {feedback.kind === "success" ? (
            <CheckCircle2 size={19} />
          ) : (
            <CircleAlert size={19} />
          )}
          <span>{feedback.message}</span>
          <button
            onClick={() => setFeedback(undefined)}
            aria-label="Đóng thông báo"
          >
            <X size={16} />
          </button>
        </div>
      )}
    </div>
  );
}
