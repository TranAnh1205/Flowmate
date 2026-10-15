const baseUrl = process.env.BASE_URL ?? "http://localhost:8090";
const stamp = `${Date.now()}-${Math.random().toString(16).slice(2)}`;
const email = `e2e-${stamp}@smarttask.local`;
const secondEmail = `e2e-other-${stamp}@smarttask.local`;
const password = "SmartTask@123";
const changedPassword = "SmartTask@456";
const adminEmail = process.env.ADMIN_EMAIL;
const adminPassword = process.env.ADMIN_PASSWORD;

let accessToken;
let refreshToken;

function check(condition, message) {
  if (!condition) throw new Error(message);
}

async function request(
  path,
  { method = "GET", body, auth = true, expected = [200], token, extraHeaders = {} } = {},
) {
  const headers = { Accept: "application/json", ...extraHeaders };
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (auth && (token || accessToken)) headers.Authorization = `Bearer ${token || accessToken}`;
  let response;
  for (let attempt = 0; attempt <= 12; attempt += 1) {
    response = await fetch(`${baseUrl}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    if (response.status !== 429 || expected.includes(429) || attempt === 12) break;
    await response.body?.cancel();
    await new Promise((resolve) => setTimeout(resolve, 13_000));
  }
  const text = await response.text();
  let data = null;
  if (text) {
    try { data = JSON.parse(text); } catch { data = text; }
  }
  if (!expected.includes(response.status)) {
    throw new Error(`${method} ${path}: expected ${expected.join("/")}, got ${response.status}: ${text}`);
  }
  return data;
}

async function eventually(action, predicate, timeoutMs = 12_000) {
  const deadline = Date.now() + timeoutMs;
  let value;
  do {
    value = await action();
    if (predicate(value)) return value;
    await new Promise((resolve) => setTimeout(resolve, 1_000));
  } while (Date.now() < deadline);
  throw new Error(`Condition was not met within ${timeoutMs} ms`);
}

console.log(`E2E target: ${baseUrl}`);

const preflight = await fetch(`${baseUrl}/api/v1/admin/users/example/status`, {
  method: "OPTIONS",
  headers: {
    Origin: "http://127.0.0.1:3000",
    "Access-Control-Request-Method": "PATCH",
    "Access-Control-Request-Headers": "X-Admin-Action-Token, Content-Type",
  },
});
check(preflight.status === 200, `CORS preflight returned ${preflight.status}`);
check(preflight.headers.get("access-control-allow-origin") === "http://127.0.0.1:3000",
  "CORS preflight did not allow the configured frontend origin");
check(/x-admin-action-token/i.test(preflight.headers.get("access-control-allow-headers") ?? ""),
  "CORS preflight did not allow the Admin action header");

await request("/api/v1/tasks", { auth: false, expected: [401] });

const registration = await request("/api/v1/auth/register", {
  method: "POST",
  auth: false,
  expected: [201],
  body: { fullName: "E2E User", email, password },
});
accessToken = registration.accessToken;
refreshToken = registration.refreshToken;
check(accessToken && refreshToken && registration.user.email === email, "Registration did not return valid tokens");
check(registration.user.role === "USER" && registration.user.status === "ACTIVE", "New account role/status is invalid");

const profile = await request("/api/v1/users/me");
check(profile.email === email, "Authenticated profile does not match the registered user");

const updatedProfile = await request("/api/v1/users/me", {
  method: "PUT",
  body: { fullName: "E2E Smart User", avatarUrl: null, timezone: "Asia/Bangkok" },
});
check(updatedProfile.fullName === "E2E Smart User", "Profile update failed");

const settings = await request("/api/v1/users/me/settings", {
  method: "PUT",
  body: {
    workStartTime: "08:30:00",
    workEndTime: "17:30:00",
    defaultReminderMinutes: 15,
    weekStartDay: 1,
    language: "vi",
    theme: "system",
  },
});
check(settings.defaultReminderMinutes === 15, "Settings update failed");

await request("/api/v1/auth/change-password", {
  method: "POST",
  expected: [401],
  body: { currentPassword: "WrongPassword123", newPassword: changedPassword },
});
const refreshTokenBeforePasswordChange = refreshToken;
await request("/api/v1/auth/change-password", {
  method: "POST",
  body: { currentPassword: password, newPassword: changedPassword },
});
await request("/api/v1/auth/refresh-token", {
  method: "POST",
  auth: false,
  expected: [401],
  body: { refreshToken: refreshTokenBeforePasswordChange },
});
await request("/api/v1/auth/login", {
  method: "POST",
  auth: false,
  expected: [401],
  body: { email, password },
});
const passwordLogin = await request("/api/v1/auth/login", {
  method: "POST",
  auth: false,
  body: { email, password: changedPassword },
});
accessToken = passwordLogin.accessToken;
refreshToken = passwordLogin.refreshToken;
check(accessToken && refreshToken, "Login with the changed password failed");

const category = await request("/api/v1/categories", {
  method: "POST",
  expected: [201],
  body: { name: `Công việc ${stamp}`, color: "#4F46E5", icon: "briefcase" },
});
const tag = await request("/api/v1/tags", {
  method: "POST",
  expected: [201],
  body: { name: `Quan trọng ${stamp}`, color: "#EF4444" },
});
await request("/api/v1/tags", {
  method: "POST",
  expected: [409],
  body: { name: `quan trọng ${stamp}`, color: "#EF4444" },
});
const updatedCategory = await request(`/api/v1/categories/${category.id}`, {
  method: "PUT",
  body: { name: `Công việc đã sửa ${stamp}`, color: "#4338CA", icon: "folder" },
});
check(updatedCategory.name.includes("đã sửa"), "Category update failed");
const updatedTag = await request(`/api/v1/tags/${tag.id}`, {
  method: "PUT",
  body: { name: `Quan trọng đã sửa ${stamp}`, color: "#DC2626" },
});
check(updatedTag.name.includes("đã sửa"), "Tag update failed");
const goal = await request("/api/v1/goals", {
  method: "POST",
  expected: [201],
  body: { title: `Mục tiêu E2E ${stamp}`, description: "Kiểm thử mục tiêu cá nhân", status: "ACTIVE", targetDate: new Date(Date.now() + 7 * 86400_000).toISOString().slice(0, 10) },
});
const updatedGoal = await request(`/api/v1/goals/${goal.id}`, {
  method: "PUT",
  body: {
    title: goal.title,
    description: "Mục tiêu đã được cập nhật",
    status: "ACTIVE",
    targetDate: goal.targetDate,
  },
});
check(updatedGoal.description === "Mục tiêu đã được cập nhật", "Goal update failed");

const dueAt = new Date(Date.now() + 60 * 60_000).toISOString();
const task = await request("/api/v1/tasks", {
  method: "POST",
  expected: [201],
  body: {
    title: `Hoàn thiện báo cáo ${stamp}`,
    description: "Công việc được tạo bởi bộ kiểm thử chấp nhận",
    priority: "HIGH",
    categoryId: category.id,
    goalId: goal.id,
    startAt: new Date().toISOString(),
    dueAt,
    estimatedMinutes: 90,
    tagIds: [tag.id],
    subtasks: [{ title: "Chuẩn bị dữ liệu" }, { title: "Viết kết luận" }],
    reminderMinutesBefore: 60,
  },
});
check(task.status === "TODO" && task.tags.length === 1 && task.subtasks.length === 2, "Task creation lost related data");

const taskPage = await request(`/api/v1/tasks?keyword=${encodeURIComponent(stamp)}&page=0&size=10`);
check(taskPage.totalElements === 1 && taskPage.content[0].id === task.id, "Task search/filter failed");
check((await request(`/api/v1/tasks/${task.id}`)).id === task.id, "Task detail failed");

const updatedTask = await request(`/api/v1/tasks/${task.id}`, {
  method: "PUT",
  body: {
    title: `Hoàn thiện báo cáo đã sửa ${stamp}`,
    description: "Kiểm tra cập nhật task vẫn giữ nhãn",
    status: "TODO",
    priority: "URGENT",
    categoryId: category.id,
    goalId: goal.id,
    startAt: task.startAt,
    dueAt: task.dueAt,
    estimatedMinutes: 120,
    actualMinutes: null,
    recurrenceRule: null,
    tagIds: [tag.id],
    reminderMinutesBefore: 60,
  },
});
check(
  updatedTask.priority === "URGENT" && updatedTask.tags.length === 1,
  "Task update with an existing tag failed",
);

await request("/api/v1/tasks?page=-1&size=10", { expected: [400] });
await request("/api/v1/tasks/not-a-uuid", { expected: [400] });
await request("/api/v1/tasks/today?timezone=Not%2FAZone", { expected: [400] });

const extraSubtask = await request(`/api/v1/tasks/${task.id}/subtasks`, {
  method: "POST",
  expected: [201],
  body: { title: "Rà soát lần cuối", position: 3 },
});
const renamedSubtask = await request(`/api/v1/tasks/${task.id}/subtasks/${extraSubtask.id}`, {
  method: "PUT",
  body: { title: "Rà soát và bàn giao", position: 3 },
});
check(renamedSubtask.title === "Rà soát và bàn giao", "Subtask update failed");
const completedSubtask = await request(`/api/v1/tasks/${task.id}/subtasks/${extraSubtask.id}/complete`, { method: "PATCH" });
check(completedSubtask.completed, "Subtask completion failed");
await request(`/api/v1/tasks/${task.id}/subtasks/${extraSubtask.id}`, {
  method: "DELETE",
  expected: [204],
});
check(
  !(await request(`/api/v1/tasks/${task.id}`)).subtasks.some((item) => item.id === extraSubtask.id),
  "Subtask deletion failed",
);

const inProgress = await request(`/api/v1/tasks/${task.id}/status`, { method: "PATCH", body: { status: "IN_PROGRESS" } });
check(inProgress.status === "IN_PROGRESS", "Task status transition failed");
await request("/api/v1/tasks/reorder", {
  method: "PATCH",
  body: { items: [{ taskId: task.id, status: "IN_PROGRESS", position: 0 }] },
});

const dashboard = await request("/api/v1/statistics/dashboard");
check(dashboard.totalTasks >= 1 && dashboard.inProgress >= 1, "Dashboard statistics failed");

const parsed = await request("/api/v1/ai/parse-task", {
  method: "POST",
  body: {
    prompt:
      "Tối mai lúc 8 giờ hoàn thành tài liệu khẩn trong 2 tiếng, nhắc tôi trước 15 phút",
  },
});
check(
  parsed.priority === "URGENT" &&
    parsed.dueAt?.includes("T20:00:00+07:00") &&
    parsed.estimatedMinutes === 120 &&
    parsed.reminderMinutesBefore === 15,
  "AI task parsing failed",
);
await request("/api/v1/ai/parse-task", {
  method: "POST",
  expected: [400],
  body: { prompt: "" },
});
const breakdown = await request("/api/v1/ai/breakdown-task", {
  method: "POST",
  body: { title: "Xây dựng website quản lý công việc", description: "Spring Boot và React", maximumSubtasks: 4 },
});
check(breakdown.subtasks.length === 4, "AI breakdown failed");
const priority = await request("/api/v1/ai/suggest-priority", {
  method: "POST",
  body: { title: "Báo cáo", dueAt, estimatedMinutes: 90 },
});
check(priority.priority === "URGENT", "AI priority suggestion failed");
await request("/api/v1/ai/daily-plan", { method: "POST", body: { date: new Date().toISOString().slice(0, 10) } });
await request("/api/v1/ai/weekly-summary", { method: "POST" });

const notifications = await eventually(
  () => request("/api/v1/notifications?page=0&size=20"),
  (value) => value.content.some((item) => item.taskId === task.id),
);
await request("/api/v1/notifications?page=-1&size=20", { expected: [400] });
const notification = notifications.content.find((item) => item.taskId === task.id);
check((await request("/api/v1/notifications/unread-count")).unreadCount >= 1, "Unread count failed");
check((await request(`/api/v1/notifications/${notification.id}/read`, { method: "PATCH" })).read, "Mark notification read failed");

const oldAccessToken = accessToken;
const refreshed = await request("/api/v1/auth/refresh-token", {
  method: "POST",
  auth: false,
  body: { refreshToken },
});
accessToken = refreshed.accessToken;
refreshToken = refreshed.refreshToken;
check(accessToken && accessToken !== oldAccessToken, "Refresh token rotation failed");

const ownerAccessToken = accessToken;
const ownerRefreshToken = refreshToken;
const other = await request("/api/v1/auth/register", {
  method: "POST",
  auth: false,
  expected: [201],
  body: { fullName: "Other E2E User", email: secondEmail, password },
});
accessToken = other.accessToken;
const otherRefreshToken = other.refreshToken;
await request(`/api/v1/tasks/${task.id}`, { expected: [404] });
await request("/api/v1/admin/users", { expected: [403] });

const project = await request("/api/v1/projects", {
  method: "POST",
  expected: [201],
  token: ownerAccessToken,
  body: { name: `Dự án E2E ${stamp}`, description: "Kiểm thử nhóm" },
});
await request(`/api/v1/projects/${project.id}`, { expected: [404] });
const invitation = await request(`/api/v1/projects/${project.id}/invitations`, {
  method: "POST",
  expected: [201],
  token: ownerAccessToken,
  body: { email: secondEmail.toUpperCase() },
});
check(invitation.invitedEmail === secondEmail, "Project invitation email mismatch");
await request(`/api/v1/projects/${project.id}/invitations`, {
  method: "POST",
  expected: [409],
  token: ownerAccessToken,
  body: { email: secondEmail },
});
const inbox = await request("/api/v1/projects/invitations");
check(inbox.some((item) => item.id === invitation.id), "Invitee cannot see project invitation");
await request("/api/v1/projects/invitations/accept", {
  method: "POST",
  body: { invitationId: invitation.id },
});
await request("/api/v1/projects/invitations/accept", {
  method: "POST",
  expected: [410],
  body: { invitationId: invitation.id },
});
await request(`/api/v1/projects/${project.id}/invitations`, {
  method: "POST",
  expected: [409],
  token: ownerAccessToken,
  body: { email: secondEmail },
});
await request(`/api/v1/projects/${project.id}/invitations`, {
  method: "POST",
  expected: [403],
  body: { email: `third-${stamp}@smarttask.local` },
});
await request(`/api/v1/projects/${project.id}/tasks`, {
  method: "POST",
  expected: [403],
  body: { title: "Không được giao cho chủ dự án", assigneeId: project.ownerId },
});
await request(`/api/v1/projects/${project.id}/tasks`, {
  method: "POST",
  expected: [400],
  token: ownerAccessToken,
  body: { title: "Người ngoài nhóm", assigneeId: crypto.randomUUID() },
});
const groupTask = await request(`/api/v1/projects/${project.id}/tasks`, {
  method: "POST",
  expected: [201],
  token: ownerAccessToken,
  body: { title: "Viết tài liệu nhóm", priority: "HIGH", assigneeId: other.user.id },
});
check((await request(`/api/v1/projects/${project.id}/tasks/${groupTask.id}`)).assigneeId === other.user.id,
  "Assigned member cannot read group task");
const finishedGroupTask = await request(`/api/v1/projects/${project.id}/tasks/${groupTask.id}/status`, {
  method: "PATCH",
  body: { status: "COMPLETED", version: groupTask.version },
});
check(finishedGroupTask.status === "COMPLETED", "Member cannot complete assigned task");
await request(`/api/v1/projects/${project.id}/tasks/${groupTask.id}`, {
  method: "PUT",
  expected: [403],
  body: {
    title: groupTask.title,
    description: groupTask.description,
    status: finishedGroupTask.status,
    priority: groupTask.priority,
    assigneeId: project.ownerId,
    dueAt: groupTask.dueAt,
    version: finishedGroupTask.version,
  },
});
await request(`/api/v1/projects/${project.id}/tasks/${groupTask.id}/status`, {
  method: "PATCH",
  expected: [409],
  token: ownerAccessToken,
  body: { status: "TODO", version: groupTask.version },
});
check((await request(`/api/v1/projects/${project.id}`, { token: ownerAccessToken })).completedTaskCount === 1,
  "Project progress is incorrect");
const archivedProject = await request(`/api/v1/projects/${project.id}`, {
  method: "PUT",
  token: ownerAccessToken,
  body: { name: project.name, description: project.description, status: "ARCHIVED" },
});
check(archivedProject.status === "ARCHIVED", "Project archive failed");
await request(`/api/v1/projects/${project.id}/tasks/${groupTask.id}`);
await request(`/api/v1/projects/${project.id}/tasks`, {
  method: "POST",
  expected: [409],
  token: ownerAccessToken,
  body: { title: "Không được thêm khi lưu trữ" },
});
await request(`/api/v1/projects/${project.id}/tasks/${groupTask.id}/status`, {
  method: "PATCH",
  expected: [409],
  body: { status: "TODO", version: finishedGroupTask.version },
});
await request(`/api/v1/projects/${project.id}`, {
  method: "PUT",
  token: ownerAccessToken,
  body: { name: project.name, description: project.description, status: "ACTIVE" },
});
await request(`/api/v1/projects/${project.id}/members/${other.user.id}`, {
  method: "DELETE",
  expected: [204],
  token: ownerAccessToken,
});
await request(`/api/v1/projects/${project.id}/tasks/${groupTask.id}`, { expected: [404] });

accessToken = ownerAccessToken;
refreshToken = ownerRefreshToken;
const completedTask = await request(`/api/v1/tasks/${task.id}/complete`, { method: "PATCH" });
check(completedTask.status === "COMPLETED" && completedTask.completedAt, "Task completion failed");
await request(`/api/v1/tasks/${task.id}`, { method: "DELETE", expected: [204] });
await request(`/api/v1/tasks/${task.id}`, { expected: [404] });

await request(`/api/v1/categories/${category.id}`, { method: "DELETE", expected: [204] });
await request(`/api/v1/tags/${tag.id}`, { method: "DELETE", expected: [204] });
await request(`/api/v1/goals/${goal.id}`, { method: "DELETE", expected: [204] });
check(
  !(await request("/api/v1/categories")).some((item) => item.id === category.id) &&
    !(await request("/api/v1/tags")).some((item) => item.id === tag.id) &&
    !(await request("/api/v1/goals")).some((item) => item.id === goal.id),
  "Supporting data deletion failed",
);

await request("/api/v1/auth/logout", { method: "POST", body: { refreshToken } });
await request("/api/v1/auth/refresh-token", {
  method: "POST",
  auth: false,
  body: { refreshToken },
  expected: [401],
});

if (adminEmail && adminPassword) {
  const adminLogin = await request("/api/v1/auth/login", {
    method: "POST",
    auth: false,
    body: { email: adminEmail, password: adminPassword },
  });
  accessToken = adminLogin.accessToken;
  refreshToken = adminLogin.refreshToken;
  check(adminLogin.user.role === "ADMIN", "Bootstrap account is not ADMIN");

  const adminProfile = await request("/api/v1/users/me");
  const adminUsers = await request(`/api/v1/admin/users?q=${encodeURIComponent(secondEmail)}&page=0&size=20`);
  check(adminUsers.totalElements === 1, "Admin user search failed");
  const managedUser = adminUsers.content[0];
  check(!("passwordHash" in managedUser) && !("refreshToken" in managedUser), "Admin API leaked credentials");
  await request("/api/v1/admin/users?page=-1&size=20", { expected: [400] });

  const userStats = await request("/api/v1/admin/statistics/users");
  const taskStats = await request("/api/v1/admin/statistics/tasks");
  const notificationStats = await request("/api/v1/admin/statistics/notifications");
  const aiStatus = await request("/api/v1/admin/ai/status");
  const systemHealth = await request("/api/v1/admin/system/health");
  check(userStats.totalUsers >= 3 && userStats.adminUsers >= 1, "Admin user statistics failed");
  check(typeof taskStats.totalTasks === "number" && !("content" in taskStats), "Task statistics leaked content");
  check(typeof notificationStats.totalNotifications === "number", "Notification statistics failed");
  check(aiStatus.mode && !("apiKey" in aiStatus), "AI status leaked or missing data");
  check(systemHealth.authService && systemHealth.taskService, "System health aggregation failed");

  await request("/api/v1/auth/reauthenticate", {
    method: "POST",
    expected: [401],
    body: { password: "wrong-admin-password" },
  });
  const reauth = await request("/api/v1/auth/reauthenticate", {
    method: "POST",
    body: { password: adminPassword },
  });
  check(reauth.adminActionToken && reauth.expiresIn <= 300, "Admin step-up authentication failed");
  const actionHeaders = { "X-Admin-Action-Token": reauth.adminActionToken };

  await request(`/api/v1/admin/users/${adminProfile.id}/status`, {
    method: "PATCH",
    expected: [409],
    extraHeaders: actionHeaders,
    body: { status: "SUSPENDED", reason: "Kiểm tra chống tự khóa" },
  });
  const suspended = await request(`/api/v1/admin/users/${managedUser.id}/status`, {
    method: "PATCH",
    extraHeaders: actionHeaders,
    body: { status: "SUSPENDED", reason: "Kiểm thử đình chỉ tài khoản" },
  });
  check(suspended.status === "SUSPENDED", "Admin failed to suspend user");
  await request(`/api/v1/admin/users/${managedUser.id}/role`, {
    method: "PATCH",
    expected: [409],
    extraHeaders: actionHeaders,
    body: { role: "ADMIN", reason: "Không được nâng quyền khi tạm ngừng" },
  });
  await request("/api/v1/auth/login", {
    method: "POST",
    auth: false,
    expected: [401],
    body: { email: secondEmail, password },
  });

  const activated = await request(`/api/v1/admin/users/${managedUser.id}/status`, {
    method: "PATCH",
    extraHeaders: actionHeaders,
    body: { status: "ACTIVE", reason: "Hoàn tất kiểm thử đình chỉ" },
  });
  check(activated.status === "ACTIVE", "Admin failed to reactivate user");
  const otherLoginAfterUnlock = await request("/api/v1/auth/login", {
    method: "POST",
    auth: false,
    body: { email: secondEmail, password },
  });
  const managedSessions = await request(`/api/v1/admin/users/${managedUser.id}/sessions`, {
    expected: [200],
  });
  const activeManagedSession = managedSessions.find((item) => item.active);
  check(activeManagedSession, "Admin session list did not contain an active session");
  await request(
    `/api/v1/admin/users/${managedUser.id}/sessions/${activeManagedSession.id}/revoke`,
    {
      method: "POST",
      extraHeaders: actionHeaders,
      body: { reason: "Kiểm thử thu hồi một phiên cụ thể" },
      expected: [204],
    },
  );
  await request("/api/v1/auth/refresh-token", {
    method: "POST",
    auth: false,
    expected: [401],
    body: { refreshToken: otherLoginAfterUnlock.refreshToken },
  });
  const finalManagedLogin = await request("/api/v1/auth/login", {
    method: "POST",
    auth: false,
    body: { email: secondEmail, password },
  });
  const revoked = await request(`/api/v1/admin/users/${managedUser.id}/sessions/revoke-all`, {
    method: "POST",
    extraHeaders: actionHeaders,
    body: { reason: "Kiểm thử thu hồi toàn bộ phiên" },
  });
  check(revoked.revokedSessions >= 1, "Admin session revocation failed");
  await request("/api/v1/auth/refresh-token", {
    method: "POST",
    auth: false,
    expected: [401],
    body: { refreshToken: finalManagedLogin.refreshToken },
  });
  await request("/api/v1/auth/refresh-token", {
    method: "POST",
    auth: false,
    expected: [401],
    body: { refreshToken: otherRefreshToken },
  });

  const promoted = await request(`/api/v1/admin/users/${registration.user.id}/role`, {
    method: "PATCH",
    extraHeaders: actionHeaders,
    body: { role: "ADMIN", reason: "Kiểm thử cấp quyền quản trị" },
  });
  check(promoted.role === "ADMIN", "Admin role promotion failed");
  const demoted = await request(`/api/v1/admin/users/${registration.user.id}/role`, {
    method: "PATCH",
    extraHeaders: actionHeaders,
    body: { role: "USER", reason: "Khôi phục quyền người dùng sau kiểm thử" },
  });
  check(demoted.role === "USER", "Admin role demotion failed");

  const auditLogs = await request("/api/v1/admin/audit-logs?page=0&size=100");
  check(
    auditLogs.content.some((item) => item.action === "USER_SUSPENDED") &&
      auditLogs.content.some((item) => item.action === "USER_ROLE_CHANGED") &&
      auditLogs.content.some((item) => item.action === "USER_SESSIONS_REVOKED"),
    "Admin audit log is incomplete",
  );
  await request("/api/v1/auth/logout", { method: "POST", body: { refreshToken } });
  console.log("Admin E2E PASSED: RBAC, privacy, search, status, role, sessions, audit, statistics and health");
} else {
  console.log("Admin E2E skipped: set ADMIN_EMAIL and ADMIN_PASSWORD to enable it");
}

console.log("E2E PASSED: auth, ownership, profile, settings, task CRUD, supporting data, AI, notifications and admin");
