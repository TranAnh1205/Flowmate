import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../auth";

export default function AdminGuard() {
  const { user } = useAuth();
  const location = useLocation();
  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  if (user.role !== "ADMIN" || user.status !== "ACTIVE") {
    return <Navigate to="/403" replace />;
  }
  return <Outlet />;
}
