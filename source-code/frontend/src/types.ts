export type Status = "TODO" | "IN_PROGRESS" | "COMPLETED" | "CANCELLED";
export type Priority = "LOW" | "MEDIUM" | "HIGH" | "URGENT";
export type UserRole = "USER" | "ADMIN";
export type AccountStatus = "ACTIVE" | "LOCKED" | "SUSPENDED" | "DEACTIVATED";
export interface User {
  id: string;
  fullName: string;
  email: string;
  avatarUrl?: string;
  timezone: string;
  role: UserRole;
  status: AccountStatus;
  createdAt: string;
  updatedAt: string;
}
export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: User;
}
export interface Subtask {
  id: string;
  title: string;
  completed: boolean;
  position: number;
  completedAt?: string;
}
export interface Tag {
  id: string;
  name: string;
  color: string;
}
export interface Category {
  id: string;
  name: string;
  color: string;
  icon?: string;
}
export interface Task {
  id: string;
  title: string;
  description?: string;
  status: Status;
  priority: Priority;
  categoryId?: string;
  goalId?: string;
  startAt?: string;
  dueAt?: string;
  estimatedMinutes?: number;
  actualMinutes?: number;
  reminderMinutesBefore?: number;
  position: number;
  recurrenceRule?: string;
  completedAt?: string;
  version: number;
  subtasks: Subtask[];
  tags: Tag[];
  createdAt: string;
  updatedAt: string;
}
export interface TaskPage {
  content: Task[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface TeamProject {
  id: string;
  name: string;
  description?: string;
  status: "ACTIVE" | "ARCHIVED";
  ownerId: string;
  myRole: "OWNER" | "MEMBER";
  memberCount: number;
  taskCount: number;
  completedTaskCount: number;
  createdAt: string;
}

export interface ProjectInvitation {
  id: string;
  projectId: string;
  projectName: string;
  invitedEmail: string;
  expiresAt: string;
}

export interface ProjectMember {
  userId: string;
  email: string;
  role: "OWNER" | "MEMBER";
  joinedAt: string;
}

export interface ProjectTask {
  id: string;
  projectId: string;
  title: string;
  description?: string;
  status: Status;
  priority: Priority;
  createdBy: string;
  assigneeId?: string;
  startAt?: string;
  dueAt?: string;
  completedAt?: string;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface ProjectTaskPage {
  content: ProjectTask[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
export interface ProjectBoard {
  id: string;
  projectId: string;
  title: string;
  description?: string;
  visibility: "WORKSPACE" | "PRIVATE";
  background: string;
  myRole: "ADMIN" | "EDITOR" | "VIEWER";
  isDefault: boolean;
  createdAt: string;
}
export interface BoardCard {
  id: string;
  title: string;
  description?: string;
  status: Status;
  priority: Priority;
  assigneeId?: string;
  startAt?: string;
  dueAt?: string;
  version: number;
  position: number;
}
export interface BoardList {
  id: string;
  title: string;
  position: number;
  workflowStatus?: Status;
  cards: BoardCard[];
}
export interface ProjectBoardDetails {
  board: ProjectBoard;
  lists: BoardList[];
}
export interface RecentBoard {
  board: ProjectBoard;
  projectName: string;
  viewedAt: string;
}
export interface CardComment {
  id: string;
  taskId: string;
  authorId: string;
  body: string;
  createdAt: string;
  updatedAt: string;
}
export interface CardChecklistItem {
  id: string;
  taskId: string;
  title: string;
  done: boolean;
  position: number;
  createdAt: string;
}
export interface CardAttachment {
  id: string;
  taskId: string;
  originalName: string;
  contentType: string;
  byteSize: number;
  uploadedBy: string;
  createdAt: string;
}
export interface BoardMessage {
  id: string;
  boardId: string;
  authorId: string;
  body: string;
  createdAt: string;
}
export interface BoardEvent {
  id: number;
  boardId: string;
  taskId?: string;
  actorId?: string;
  kind: string;
  createdAt: string;
}
export interface BoardLabel {
  id: string;
  boardId: string;
  name: string;
  color: string;
}
export interface DashboardStats {
  totalTasks: number;
  todo: number;
  inProgress: number;
  completed: number;
  cancelled: number;
  overdue: number;
  dueToday: number;
  weeklyCompletionRate: number;
}
export interface NotificationItem {
  id: string;
  taskId?: string;
  title: string;
  message: string;
  type: string;
  read: boolean;
  createdAt: string;
  readAt?: string;
}
