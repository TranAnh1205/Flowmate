const baseUrl = process.env.BASE_URL ?? "http://localhost:8090";
const email = process.env.DEMO_EMAIL;
const password = process.env.DEMO_PASSWORD;

if (!email || !password) throw new Error("Set DEMO_EMAIL and DEMO_PASSWORD.");

let accessToken;
async function request(path, { method = "GET", body, auth = true, expected = [200] } = {}) {
  for (let attempt = 1; attempt <= 8; attempt++) {
    const headers = { Accept: "application/json" };
    if (body !== undefined) headers["Content-Type"] = "application/json";
    if (auth && accessToken) headers.Authorization = `Bearer ${accessToken}`;
    const response = await fetch(`${baseUrl}${path}`, {
      method, headers, body: body === undefined ? undefined : JSON.stringify(body),
    });
    const text = await response.text();
    let data = null;
    if (text) {
      try { data = JSON.parse(text); } catch { data = text; }
    }
    if (response.status === 429 && attempt < 8) {
      const retryAfter = Number(response.headers.get("retry-after") ?? 3);
      await new Promise((resolve) => setTimeout(resolve, Math.max(1, retryAfter) * 1_000));
      continue;
    }
    if (!expected.includes(response.status)) {
      throw new Error(`${method} ${path}: expected ${expected.join("/")}, got ${response.status}: ${text}`);
    }
    return { data, status: response.status };
  }
}

const login = await request("/api/v1/auth/login", {
  method: "POST", auth: false, body: { email, password },
});
accessToken = login.data.accessToken;

const profile = (await request("/api/v1/users/me")).data;
const settings = (await request("/api/v1/users/me/settings")).data;
const tasks = (await request("/api/v1/tasks?page=0&size=100")).data;
const categories = (await request("/api/v1/categories")).data;
const tags = (await request("/api/v1/tags")).data;
const goals = (await request("/api/v1/goals")).data;
const dashboard = (await request("/api/v1/statistics/dashboard")).data;
const notifications = (await request("/api/v1/notifications?page=0&size=20")).data;
const unread = (await request("/api/v1/notifications/unread-count")).data;
const adminDenied = await request("/api/v1/admin/users", { expected: [403] });

const parsed = (await request("/api/v1/ai/parse-task", {
  method: "POST",
  body: { prompt: "Ngày mai lúc 9 giờ hoàn thiện slide bảo vệ trong 2 tiếng, ưu tiên cao và nhắc trước 30 phút" },
})).data;
const breakdown = (await request("/api/v1/ai/breakdown-task", {
  method: "POST",
  body: { title: "Chuẩn bị bảo vệ đồ án", description: "Báo cáo, slide và demo hệ thống", maximumSubtasks: 4 },
})).data;
const priority = (await request("/api/v1/ai/suggest-priority", {
  method: "POST",
  body: { title: "Hoàn thiện báo cáo đồ án", dueAt: new Date(Date.now() + 86_400_000).toISOString(), estimatedMinutes: 180 },
})).data;
const dailyPlan = (await request("/api/v1/ai/daily-plan", {
  method: "POST", body: { date: new Date().toISOString().slice(0, 10) },
})).data;
const weeklySummary = (await request("/api/v1/ai/weekly-summary", { method: "POST" })).data;

const statusCounts = tasks.content.reduce((result, task) => {
  result[task.status] = (result[task.status] ?? 0) + 1;
  return result;
}, {});

console.log(JSON.stringify({
  authentication: { email: profile.email, role: profile.role, status: profile.status },
  profileAndSettings: { timezone: profile.timezone, theme: settings.theme, language: settings.language },
  taskManagement: { total: tasks.totalElements, statusCounts },
  supportingData: { categories: categories.length, tags: tags.length, goals: goals.length },
  dashboard,
  notifications: { total: notifications.totalElements, unread: unread.unreadCount },
  authorization: { regularUserAdminStatus: adminDenied.status },
  ai: {
    parsedTitle: parsed.title,
    parsedPriority: parsed.priority,
    breakdownItems: breakdown.subtasks?.length ?? 0,
    suggestedPriority: priority.priority,
    dailyPlanReady: Boolean(dailyPlan),
    weeklySummaryReady: Boolean(weeklySummary),
  },
}, null, 2));
