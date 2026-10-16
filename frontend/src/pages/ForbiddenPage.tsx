import { ShieldX } from "lucide-react";
import { Link } from "react-router-dom";

export default function ForbiddenPage() {
  return (
    <main className="forbidden-page">
      <div>
        <ShieldX size={54} />
        <span className="eyebrow">403 · KHÔNG CÓ QUYỀN</span>
        <h1>Bạn không thể truy cập khu vực này</h1>
        <p>Tài khoản hiện tại không có quyền quản trị hệ thống Flowmate.</p>
        <Link className="primary" to="/app/dashboard">
          Trở về không gian cá nhân
        </Link>
      </div>
    </main>
  );
}
