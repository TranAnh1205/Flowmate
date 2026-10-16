import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { ArrowRight, Bot, CheckCircle2, Sparkles } from "lucide-react";
import { api, getApiErrorMessage } from "../api";
import { useAuth } from "../auth";
import type { AuthResponse } from "../types";
export default function AuthPage({ mode }: { mode: "login" | "register" }) {
  const [form, setForm] = useState({ fullName: "", email: "", password: "" });
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const { login } = useAuth();
  const nav = useNavigate();
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError("");
    try {
      const { data } = await api.post<AuthResponse>(`/auth/${mode}`, form);
      login(data);
      nav(data.user.role === "ADMIN" ? "/admin/dashboard" : "/app/dashboard");
    } catch (err: unknown) {
      setError(getApiErrorMessage(err, "Không thể kết nối. Vui lòng thử lại."));
    } finally {
      setLoading(false);
    }
  };
  return (
    <div className="auth-page">
      <section className="auth-story">
        <div className="auth-logo">
          <span>F</span>Flowmate
        </div>
        <div>
          <span className="eyebrow light">
            <Sparkles size={15} /> LÀM VIỆC THÔNG MINH HƠN
          </span>
          <h1>
            Biến mọi dự định thành một ngày <em>thật rõ ràng.</em>
          </h1>
          <p>
            Quản lý công việc cá nhân, ưu tiên điều quan trọng và để AI giúp bạn
            chia nhỏ những mục tiêu khó bắt đầu.
          </p>
          <ul>
            <li>
              <CheckCircle2 />
              Kế hoạch ngày tập trung
            </li>
            <li>
              <CheckCircle2 />
              Kanban và lịch trong một nơi
            </li>
            <li>
              <CheckCircle2 />
              Trợ lý AI hiểu tiếng Việt
            </li>
          </ul>
        </div>
        <small>Thiết kế cho nhịp làm việc cá nhân.</small>
      </section>
      <section className="auth-panel">
        <form onSubmit={submit}>
          <div className="mobile-auth-logo">
            <span>F</span>Flowmate
          </div>
          <span className="eyebrow">
            {mode === "login" ? "CHÀO MỪNG TRỞ LẠI" : "BẮT ĐẦU HÀNH TRÌNH"}
          </span>
          <h2>
            {mode === "login"
              ? "Đăng nhập vào không gian của bạn"
              : "Tạo tài khoản mới"}
          </h2>
          <p>
            {mode === "login"
              ? "Tiếp tục từ nơi bạn đã dừng lại."
              : "Thiết lập không gian làm việc cá nhân trong một phút."}
          </p>
          {mode === "register" && (
            <label>
              Họ và tên
              <input
                required
                autoComplete="name"
                value={form.fullName}
                onChange={(e) => setForm({ ...form, fullName: e.target.value })}
                placeholder="Nguyễn Văn A"
              />
            </label>
          )}
          <label>
            Email
            <input
              required
              type="email"
              autoComplete="email"
              value={form.email}
              onChange={(e) => setForm({ ...form, email: e.target.value })}
              placeholder="ban@example.com"
            />
          </label>
          <label>
            Mật khẩu
            <input
              required
              minLength={8}
              type="password"
              autoComplete={
                mode === "login" ? "current-password" : "new-password"
              }
              value={form.password}
              onChange={(e) => setForm({ ...form, password: e.target.value })}
              placeholder="Tối thiểu 8 ký tự"
            />
          </label>
          {error && (
            <div className="form-error" role="alert">
              {error}
            </div>
          )}
          <button className="primary auth-submit" disabled={loading}>
            {loading
              ? "Đang xử lý..."
              : mode === "login"
                ? "Đăng nhập"
                : "Tạo tài khoản"}
            <ArrowRight size={18} />
          </button>
          <div className="auth-switch">
            {mode === "login" ? (
              <>
                Chưa có tài khoản? <Link to="/register">Đăng ký ngay</Link>
              </>
            ) : (
              <>
                Đã có tài khoản? <Link to="/login">Đăng nhập</Link>
              </>
            )}
          </div>
          <div className="ai-note">
            <Bot size={18} />
            <span>
              AI chỉ đưa ra đề xuất. Bạn luôn là người quyết định cuối cùng.
            </span>
          </div>
        </form>
      </section>
    </div>
  );
}
