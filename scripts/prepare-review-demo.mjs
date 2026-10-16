const baseUrl = process.env.BASE_URL ?? "http://localhost:8090";
const email = process.env.DEMO_EMAIL;
const password = process.env.DEMO_PASSWORD;

if (!email || !password) {
  throw new Error("Set DEMO_EMAIL and DEMO_PASSWORD before running this script.");
}

let accessToken;

async function request(path, { method = "GET", body, auth = true, expected = [200] } = {}) {
  for (let attempt = 1; attempt <= 6; attempt++) {
    const headers = { Accept: "application/json" };
    if (body !== undefined) headers["Content-Type"] = "application/json";
    if (auth && accessToken) headers.Authorization = `Bearer ${accessToken}`;
    const response = await fetch(`${baseUrl}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    const text = await response.text();
    let data = null;
    if (text) {
      try { data = JSON.parse(text); } catch { data = text; }
    }
    if (response.status === 429 && attempt < 6) {
      const retryAfter = Number(response.headers.get("retry-after") ?? 2);
      await new Promise((resolve) => setTimeout(resolve, Math.max(1, retryAfter) * 1_000));
      continue;
    }
    if (!expected.includes(response.status)) {
      throw new Error(`${method} ${path}: expected ${expected.join("/")}, got ${response.status}: ${text}`);
    }
    return data;
  }
}

async function loginOrRegister() {
  const loginResponse = await request("/api/v1/auth/login", {
    method: "POST",
    auth: false,
    expected: [200, 401],
    body: { email, password },
  });
  if (loginResponse?.accessToken) return loginResponse;
  return request("/api/v1/auth/register", {
    method: "POST",
    auth: false,
    expected: [201],
    body: { fullName: "Người dùng Review", email, password },
  });
}

function isoAfterMinutes(minutes) {
  return new Date(Date.now() + minutes * 60_000).toISOString();
}

function dateAfterDays(days) {
  return new Date(Date.now() + days * 86_400_000).toISOString().slice(0, 10);
}

const session = await loginOrRegister();
accessToken = session.accessToken;

await request("/api/v1/users/me", {
  method: "PUT",
  body: { fullName: "Người dùng Review", avatarUrl: null, timezone: "Asia/Bangkok" },
});
await request("/api/v1/users/me/settings", {
  method: "PUT",
  body: {
    workStartTime: "08:00:00",
    workEndTime: "17:30:00",
    defaultReminderMinutes: 30,
    weekStartDay: 1,
    language: "vi",
    theme: "system",
  },
});

async function ensureCategory(name, color, icon) {
  const existing = (await request("/api/v1/categories")).find((item) => item.name === name);
  return existing ?? request("/api/v1/categories", {
    method: "POST", expected: [201], body: { name, color, icon },
  });
}

async function ensureTag(name, color) {
  const existing = (await request("/api/v1/tags")).find((item) => item.name === name);
  return existing ?? request("/api/v1/tags", {
    method: "POST", expected: [201], body: { name, color },
  });
}

async function ensureGoal(title, description, targetDays) {
  const existing = (await request("/api/v1/goals")).find((item) => item.title === title);
  return existing ?? request("/api/v1/goals", {
    method: "POST",
    expected: [201],
    body: { title, description, status: "ACTIVE", targetDate: dateAfterDays(targetDays) },
  });
}

const categoryProject = await ensureCategory("Đồ án chuyên ngành", "#6366F1", "folder");
const categoryStudy = await ensureCategory("Học tập", "#0EA5E9", "book");
const categoryPersonal = await ensureCategory("Cá nhân", "#10B981", "user");

const tagImportant = await ensureTag("Quan trọng", "#EF4444");
const tagFocus = await ensureTag("Cần tập trung", "#F59E0B");
const tagQuick = await ensureTag("Việc nhanh", "#22C55E");
const tagAi = await ensureTag("AI hỗ trợ", "#8B5CF6");

const goalProject = await ensureGoal(
  "Hoàn thành đồ án chuyên ngành",
  "Hoàn thiện chương trình, báo cáo và phần trình bày bảo vệ.",
  14,
);
const goalSpring = await ensureGoal(
  "Nâng cao kiến thức Spring Boot",
  "Nắm chắc Microservices, JWT, Gateway và kiểm thử.",
  30,
);

const taskDefinitions = [
  {
    title: "Hoàn thiện báo cáo ĐACN",
    description: "Rà soát nội dung, định dạng, hình ảnh và sự thống nhất giữa báo cáo với source code.",
    priority: "URGENT", status: "IN_PROGRESS", categoryId: categoryProject.id, goalId: goalProject.id,
    dueAt: isoAfterMinutes(2 * 24 * 60), estimatedMinutes: 240,
    tagIds: [tagImportant.id, tagFocus.id], reminderMinutesBefore: 60,
    subtasks: [
      { title: "Kiểm tra mục lục và đánh số hình" },
      { title: "Rà soát Chương 2 và các biểu đồ UML" },
      { title: "Cập nhật bằng chứng kiểm thử" },
      { title: "Xuất bản PDF cuối cùng" },
    ],
  },
  {
    title: "Chuẩn bị slide bảo vệ",
    description: "Tóm tắt bài toán, kiến trúc, chức năng, điểm mới và kết quả kiểm thử.",
    priority: "HIGH", status: "TODO", categoryId: categoryProject.id, goalId: goalProject.id,
    dueAt: isoAfterMinutes(3 * 24 * 60), estimatedMinutes: 180,
    tagIds: [tagImportant.id], reminderMinutesBefore: 120,
    subtasks: [
      { title: "Thiết kế slide kiến trúc" },
      { title: "Chọn ảnh minh họa chức năng" },
      { title: "Chuẩn bị phần kết luận" },
    ],
  },
  {
    title: "Luyện tập phần trình bày 10 phút",
    description: "Thực hành giới thiệu project theo đúng luồng và kiểm soát thời gian.",
    priority: "MEDIUM", status: "TODO", categoryId: categoryProject.id, goalId: goalProject.id,
    dueAt: isoAfterMinutes(4 * 24 * 60), estimatedMinutes: 60,
    tagIds: [tagFocus.id], reminderMinutesBefore: 60,
    subtasks: [{ title: "Luyện phần mở đầu" }, { title: "Luyện demo chức năng" }, { title: "Chuẩn bị câu hỏi phản biện" }],
  },
  {
    title: "Ôn tập kiến trúc Microservices",
    description: "Ôn vai trò Gateway, từng service, database per service và luồng JWT RS256.",
    priority: "HIGH", status: "IN_PROGRESS", categoryId: categoryStudy.id, goalId: goalSpring.id,
    dueAt: isoAfterMinutes(24 * 60), estimatedMinutes: 120,
    tagIds: [tagFocus.id], reminderMinutesBefore: 30,
    subtasks: [{ title: "Vẽ lại luồng request" }, { title: "Ôn cơ chế cô lập userId" }, { title: "Ôn rate limit Redis" }],
  },
  {
    title: "Hoàn thiện chức năng quản lý công việc",
    description: "CRUD công việc, checklist, bộ lọc và xử lý trạng thái đã hoàn tất.",
    priority: "HIGH", status: "COMPLETED", categoryId: categoryProject.id, goalId: goalProject.id,
    dueAt: isoAfterMinutes(6 * 60), estimatedMinutes: 300,
    tagIds: [tagAi.id], reminderMinutesBefore: null,
    subtasks: [{ title: "Kiểm tra CRUD" }, { title: "Kiểm tra checklist" }, { title: "Kiểm tra bộ lọc" }],
  },
  {
    title: "Kiểm thử API bằng Postman",
    description: "Bộ kiểm thử API đã đạt đầy đủ assertion và có ảnh bằng chứng trong báo cáo.",
    priority: "MEDIUM", status: "COMPLETED", categoryId: categoryProject.id, goalId: goalProject.id,
    dueAt: isoAfterMinutes(8 * 60), estimatedMinutes: 90,
    tagIds: [tagQuick.id], reminderMinutesBefore: null,
    subtasks: [{ title: "Chạy collection" }, { title: "Kiểm tra assertion" }, { title: "Chụp kết quả" }],
  },
  {
    title: "Rà soát tài liệu tham khảo",
    description: "Kiểm tra tài liệu Spring, React, PostgreSQL, Docker và OpenAI được trích dẫn thống nhất.",
    priority: "LOW", status: "TODO", categoryId: categoryStudy.id, goalId: goalSpring.id,
    dueAt: isoAfterMinutes(7 * 24 * 60), estimatedMinutes: 45,
    tagIds: [tagQuick.id], reminderMinutesBefore: 60,
    subtasks: [{ title: "Kiểm tra nguồn chính thức" }, { title: "Chuẩn hóa cách ghi tài liệu" }],
  },
  {
    title: "Kiểm tra cơ chế nhắc việc",
    description: "Công việc dùng để minh họa Notification Service tạo thông báo đúng người dùng.",
    priority: "MEDIUM", status: "TODO", categoryId: categoryPersonal.id, goalId: null,
    dueAt: isoAfterMinutes(40), estimatedMinutes: 20,
    tagIds: [tagQuick.id], reminderMinutesBefore: 60,
    subtasks: [{ title: "Mở trung tâm thông báo" }, { title: "Đánh dấu thông báo đã đọc" }],
  },
];

const currentTasks = await request("/api/v1/tasks?page=0&size=100");
for (const definition of taskDefinitions) {
  if (currentTasks.content.some((item) => item.title === definition.title)) continue;
  const created = await request("/api/v1/tasks", {
    method: "POST",
    expected: [201],
    body: {
      title: definition.title,
      description: definition.description,
      priority: definition.priority,
      categoryId: definition.categoryId,
      goalId: definition.goalId,
      startAt: new Date().toISOString(),
      dueAt: definition.dueAt,
      estimatedMinutes: definition.estimatedMinutes,
      tagIds: definition.tagIds,
      subtasks: definition.subtasks,
      reminderMinutesBefore: definition.reminderMinutesBefore,
    },
  });
  if (definition.status === "IN_PROGRESS") {
    await request(`/api/v1/tasks/${created.id}/status`, {
      method: "PATCH", body: { status: "IN_PROGRESS" },
    });
  }
  if (definition.status === "COMPLETED") {
    for (const subtask of created.subtasks) {
      await request(`/api/v1/tasks/${created.id}/subtasks/${subtask.id}/complete`, { method: "PATCH" });
    }
    await request(`/api/v1/tasks/${created.id}/complete`, { method: "PATCH" });
  }
}

const finalTasks = await request("/api/v1/tasks?page=0&size=100");
const dashboard = await request("/api/v1/statistics/dashboard");
await new Promise((resolve) => setTimeout(resolve, 7_000));
const notifications = await request("/api/v1/notifications?page=0&size=20");

console.log(JSON.stringify({
  email,
  categories: (await request("/api/v1/categories")).length,
  tags: (await request("/api/v1/tags")).length,
  goals: (await request("/api/v1/goals")).length,
  tasks: finalTasks.totalElements,
  dashboard,
  notifications: notifications.totalElements,
}, null, 2));
