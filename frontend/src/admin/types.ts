import type { AccountStatus, UserRole } from "../types";

export interface AdminUser {
  id: string;
  fullName: string;
  email: string;
  avatarUrl?: string;
  timezone: string;
  role: UserRole;
  status: AccountStatus;
  statusReason?: string;
  statusChangedAt?: string;
  statusChangedBy?: string;
  lastLoginAt?: string;
  activeSessions: number;
  createdAt: string;
  updatedAt: string;
}

export interface AdminUserPage {
  content: AdminUser[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface UserStatistics {
  totalUsers: number;
  activeUsers: number;
  lockedUsers: number;
  suspendedUsers: number;
  deactivatedUsers: number;
  adminUsers: number;
  newUsersToday: number;
  newUsersThisWeek: number;
}

export interface TaskSystemStatistics {
  totalTasks: number;
  todo: number;
  inProgress: number;
  completed: number;
  cancelled: number;
  overdue: number;
  createdToday: number;
  createdThisWeek: number;
  completionRate: number;
}

export interface NotificationSystemStatistics {
  totalNotifications: number;
  unreadNotifications: number;
  createdToday: number;
  pendingReminders: number;
  processingReminders: number;
  processedReminders: number;
  overduePendingReminders: number;
}

export interface AiStatus {
  enabled: boolean;
  mode: string;
  model: string;
  provider: string;
  checkedAt: string;
}

export interface ServiceHealth {
  status: "UP" | "DOWN";
  responseTimeMs: number;
  checkedAt: string;
}

export interface SystemHealth {
  authService: ServiceHealth;
  taskService: ServiceHealth;
  aiService: ServiceHealth;
  notificationService: ServiceHealth;
  status: "UP" | "DEGRADED";
  checkedAt: string;
}

export interface AdminSession {
  id: string;
  deviceName?: string;
  ipAddress?: string;
  userAgent?: string;
  createdAt: string;
  lastUsedAt?: string;
  expiresAt: string;
  revokedAt?: string;
  revokeReason?: string;
  active: boolean;
}

export interface AuditLog {
  id: string;
  actorUserId: string;
  actorRole: string;
  action: string;
  targetType: string;
  targetId?: string;
  reason?: string;
  previousState?: string;
  newState?: string;
  result: string;
  ipAddress?: string;
  userAgent?: string;
  metadata?: string;
  createdAt: string;
}

export interface AuditLogPage {
  content: AuditLog[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
