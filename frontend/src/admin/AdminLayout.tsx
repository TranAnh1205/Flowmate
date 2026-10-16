import { useState } from "react";
import { NavLink, Outlet, useNavigate } from "react-router-dom";
import {
  Activity,
  ArrowLeft,
  FileClock,
  LayoutDashboard,
  LogOut,
  Menu,
  ShieldCheck,
  Users,
  X,
} from "lucide-react";
import { useAuth } from "../auth";
import { api } from "../api";

const links = [
  ["/admin/dashboard", "Tổng quan", LayoutDashboard],
  ["/admin/users", "Người dùng", Users],
  ["/admin/audit-logs", "Nhật ký quản trị", FileClock],
  ["/admin/system", "Trạng thái hệ thống", Activity],
] as const;

export default function AdminLayout() {
  const [mobileOpen, setMobileOpen] = useState(false);
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const leave = async () => {
    const refreshToken = localStorage.getItem("refreshToken");
    try {
      if (refreshToken) {
        await api.post("/auth/logout", { refreshToken });
      }
    } finally {
      logout();
      navigate("/login");
    }
  };

  return (
    <div className="admin-shell">
      <a className="skip-link" href="#admin-main-content">
        Đi đến nội dung quản trị
      </a>
      <aside className={`admin-sidebar ${mobileOpen ? "mobile-open" : ""}`}>
        <div className="admin-brand">
          <div className="brand-mark">
            <ShieldCheck size={21} />
          </div>
          <div>
            <strong>Flowmate Admin</strong>
            <span>Quản trị an toàn</span>
          </div>
          <button
            className="admin-close-mobile"
            onClick={() => setMobileOpen(false)}
            aria-label="Đóng menu quản trị"
          >
            <X />
          </button>
        </div>
        <nav>
          {links.map(([to, label, Icon]) => (
            <NavLink
              key={to}
              to={to}
              onClick={() => setMobileOpen(false)}
              className={({ isActive }) => (isActive ? "active" : "")}
            >
              <Icon size={19} />
              <span>{label}</span>
            </NavLink>
          ))}
        </nav>
        <div className="admin-sidebar-footer">
          <button onClick={() => navigate("/app/dashboard")}>
            <ArrowLeft size={18} />
            Không gian cá nhân
          </button>
          <div className="admin-identity">
            <span className="avatar">
              {user?.fullName.charAt(0).toUpperCase()}
            </span>
            <span>
              <strong>{user?.fullName}</strong>
              <small>Administrator</small>
            </span>
          </div>
          <button onClick={leave}>
            <LogOut size={18} />
            Đăng xuất
          </button>
        </div>
      </aside>
      <main className="admin-main">
        <header className="admin-topbar">
          <button
            className="icon-btn admin-mobile-menu"
            onClick={() => setMobileOpen(true)}
            aria-label="Mở menu quản trị"
          >
            <Menu />
          </button>
          <div>
            <span>TRUNG TÂM QUẢN TRỊ</span>
            <strong>Dữ liệu tổng hợp, không đọc nội dung cá nhân</strong>
          </div>
          <span className="admin-role-pill">
            <ShieldCheck size={15} /> ADMIN
          </span>
        </header>
        <div className="admin-content" id="admin-main-content" tabIndex={-1}>
          <Outlet />
        </div>
      </main>
    </div>
  );
}
