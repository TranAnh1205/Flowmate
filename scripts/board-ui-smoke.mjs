import path from "node:path";
import { createRequire } from "node:module";

const base = process.env.BASE_URL ?? "http://127.0.0.1:3000";
const modules = process.env.CODEX_NODE_MODULES;
if (!modules) throw new Error("CODEX_NODE_MODULES is required");
const require = createRequire(import.meta.url);
const { chromium } = require(path.join(modules, "playwright"));
const edge = process.env.EDGE_PATH ??
  "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe";
const password = "BoardUiSmoke@123";
const stamp = Date.now().toString(36);
const email = `board-ui-${stamp}@smarttask.local`;

async function json(pathname, method = "GET", body, token) {
  const response = await fetch(base + pathname, {
    method,
    headers: {
      Accept: "application/json",
      ...(body === undefined ? {} : { "Content-Type": "application/json" }),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  if (!response.ok) throw new Error(`${method} ${pathname}: ${response.status} ${text}`);
  return text ? JSON.parse(text) : null;
}

const auth = await json("/api/v1/auth/register", "POST",
  { fullName: "Board UI Tester", email, password });
const project = await json("/api/v1/projects", "POST", { name: "Board UI Smoke" }, auth.accessToken);
const boards = await json(`/api/v1/projects/${project.id}/boards`, "GET", undefined, auth.accessToken);
const board = boards[0];
const runtimeErrors = [];
const browser = await chromium.launch({ executablePath: edge, headless: true });
try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  page.on("pageerror", (error) => runtimeErrors.push(error.message));
  await page.goto(base + "/login");
  await page.evaluate((session) => {
    localStorage.setItem("accessToken", session.accessToken);
    localStorage.setItem("refreshToken", session.refreshToken);
    localStorage.setItem("user", JSON.stringify(session.user));
  }, auth);
  await page.goto(base + `/app/projects/${project.id}/boards/${board.id}`);
  await page.getByRole("heading", { name: "Bảng công việc" }).waitFor();
  const title = `UI card ${stamp}`;
  await page.getByLabel("Tên thẻ mới trong Cần làm").fill(title);
  await page.locator(".team-board-add-card").first().getByRole("button", { name: "Thêm" }).click();
  await page.getByRole("button", { name: title, exact: true }).waitFor();
  await page.getByRole("button", { name: title, exact: true }).click();
  await page.getByRole("dialog", { name: `Chi tiết thẻ ${title}` }).waitFor();
  await page.getByLabel("Bắt đầu").fill("2026-11-01T09:00");
  await page.getByLabel("Hạn hoàn thành").fill("2026-11-01T10:00");
  await page.getByRole("button", { name: "Lưu thẻ" }).click();
  await page.getByText(/^Bắt đầu /).waitFor();
  await page.getByLabel("Mục checklist mới").fill("Kiểm tra UI");
  await page.getByRole("dialog").getByRole("button", { name: "Thêm", exact: true }).click();
  await page.getByText("Kiểm tra UI").waitFor();
  await page.getByLabel("Nội dung bình luận").fill("Bình luận UI");
  await page.getByRole("button", { name: "Gửi bình luận" }).click();
  await page.getByText("Bình luận UI").waitFor();
  await page.getByRole("button", { name: "Đóng" }).click();
  await page.getByLabel("Tin nhắn mới").fill("Tin nhắn UI");
  await page.getByRole("button", { name: "Gửi", exact: true }).click();
  await page.getByText("Tin nhắn UI").waitFor();
  await page.getByRole("button", { name: "Bảng dữ liệu" }).click();
  await page.getByRole("table").getByRole("button", { name: title }).waitFor();
  await page.getByRole("button", { name: "Lịch", exact: true }).click();
  await page.getByRole("button", { name: "Thống kê" }).click();
  await page.getByText("Cài đặt bảng", { exact: true }).click();
  const copiedTitle = `UI copied board ${stamp}`;
  await page.getByLabel("Sao chép bảng").fill(copiedTitle);
  await page.getByRole("button", { name: "Tạo bản sao" }).click();
  await page.getByRole("heading", { name: copiedTitle }).waitFor();
  await page.getByRole("button", { name: title, exact: true }).waitFor();
  if (runtimeErrors.length) throw new Error(runtimeErrors.join("\n"));
  console.log("Board UI smoke passed: create card, start/due dates, checklist, comment, chat, table/calendar/dashboard views, and board copy.");
} finally {
  await browser.close();
}
