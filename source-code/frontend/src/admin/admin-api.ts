import { api } from "../api";
import type { AccountStatus, UserRole } from "../types";
import type {
  AdminSession,
  AdminUser,
  AdminUserPage,
  AiStatus,
  AuditLogPage,
  NotificationSystemStatistics,
  SystemHealth,
  TaskSystemStatistics,
  UserStatistics,
} from "./types";

export async function getAdminUsers(params: {
  page: number;
  size: number;
  q?: string;
  role?: UserRole | "";
  status?: AccountStatus | "";
}) {
  return (await api.get<AdminUserPage>("/admin/users", { params })).data;
}

export async function getAdminUser(userId: string) {
  return (await api.get<AdminUser>(`/admin/users/${userId}`)).data;
}

export async function getAdminSessions(userId: string) {
  return (await api.get<AdminSession[]>(`/admin/users/${userId}/sessions`))
    .data;
}

export async function reauthenticateAdmin(password: string) {
  return (
    await api.post<{ adminActionToken: string; expiresIn: number }>(
      "/auth/reauthenticate",
      { password },
    )
  ).data;
}

export async function changeAdminUserStatus(
  userId: string,
  status: AccountStatus,
  reason: string,
  actionToken: string,
) {
  return (
    await api.patch<AdminUser>(
      `/admin/users/${userId}/status`,
      { status, reason },
      { headers: { "X-Admin-Action-Token": actionToken } },
    )
  ).data;
}

export async function changeAdminUserRole(
  userId: string,
  role: UserRole,
  reason: string,
  actionToken: string,
) {
  return (
    await api.patch<AdminUser>(
      `/admin/users/${userId}/role`,
      { role, reason },
      { headers: { "X-Admin-Action-Token": actionToken } },
    )
  ).data;
}

export async function revokeAdminUserSessions(
  userId: string,
  reason: string,
  actionToken: string,
) {
  return (
    await api.post<{ message: string; revokedSessions: number }>(
      `/admin/users/${userId}/sessions/revoke-all`,
      { reason },
      { headers: { "X-Admin-Action-Token": actionToken } },
    )
  ).data;
}

export async function revokeAdminUserSession(
  userId: string,
  sessionId: string,
  reason: string,
  actionToken: string,
) {
  await api.post(
    `/admin/users/${userId}/sessions/${sessionId}/revoke`,
    { reason },
    { headers: { "X-Admin-Action-Token": actionToken } },
  );
}

export async function getAdminUserStatistics() {
  return (await api.get<UserStatistics>("/admin/statistics/users")).data;
}

export async function getAdminTaskStatistics() {
  return (await api.get<TaskSystemStatistics>("/admin/statistics/tasks")).data;
}

export async function getAdminNotificationStatistics() {
  return (
    await api.get<NotificationSystemStatistics>(
      "/admin/statistics/notifications",
    )
  ).data;
}

export async function getAdminAiStatus() {
  return (await api.get<AiStatus>("/admin/ai/status")).data;
}

export async function getAdminSystemHealth() {
  return (await api.get<SystemHealth>("/admin/system/health")).data;
}

export async function getAuditLogs(params: {
  page: number;
  size: number;
  action?: string;
  result?: string;
}) {
  return (await api.get<AuditLogPage>("/admin/audit-logs", { params })).data;
}
