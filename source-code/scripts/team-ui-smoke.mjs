import fs from "node:fs/promises";
import path from "node:path";
import { createRequire } from "node:module";

const baseUrl = process.env.BASE_URL ?? "http://127.0.0.1:3000";
const nodeModules = process.env.CODEX_NODE_MODULES;
const edgePath = process.env.EDGE_PATH ??
  "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe";
if (!nodeModules) throw new Error("CODEX_NODE_MODULES must point to a folder containing playwright");

const require = createRequire(import.meta.url);
const { chromium } = require(path.join(nodeModules, "playwright"));
const artifacts = process.env.TEAM_UI_ARTIFACTS
  ? path.resolve(process.env.TEAM_UI_ARTIFACTS)
  : path.resolve("..", "artifacts", "team-ui-smoke");
const stamp = `${Date.now()}-${Math.random().toString(16).slice(2)}`;
const password = "TeamUiSmoke@123";
const ownerEmail = `team-owner-${stamp}@smarttask.local`;
const memberEmail = `team-member-${stamp}@smarttask.local`;
const projectName = `Dự án UI ${stamp}`;
const taskTitle = `Kiểm thử giao việc ${stamp}`;
const assertions = [];
const runtimeErrors = [];

function check(condition, message) {
  if (!condition) throw new Error(message);
  assertions.push(message);
}

async function jsonRequest(requestPath, options = {}) {
  const response = await fetch(`${baseUrl}${requestPath}`, {
    ...options,
    headers: { "Content-Type": "application/json", ...(options.headers ?? {}) },
  });
  const text = await response.text();
  if (!response.ok) throw new Error(`${options.method ?? "GET"} ${requestPath}: ${response.status} ${text}`);
  return text ? JSON.parse(text) : null;
}

await fs.mkdir(artifacts, { recursive: true });
const owner = await jsonRequest("/api/v1/auth/register", {
  method: "POST",
  body: JSON.stringify({ fullName: "Team UI Owner", email: ownerEmail, password }),
});
const member = await jsonRequest("/api/v1/auth/register", {
  method: "POST",
  body: JSON.stringify({ fullName: "Team UI Member", email: memberEmail, password }),
});

const browser = await chromium.launch({ executablePath: edgePath, headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
page.on("pageerror", (error) => runtimeErrors.push(`pageerror: ${error.message}`));
page.on("console", (message) => {
  if (message.type() === "error") runtimeErrors.push(`console: ${message.text()}`);
});
page.on("requestfailed", (request) => {
  runtimeErrors.push(`requestfailed: ${request.method()} ${request.url()} ${request.failure()?.errorText ?? "unknown"}`);
});
page.on("response", (response) => {
  if (response.status() === 429) {
    runtimeErrors.push(`http 429: ${response.request().method()} ${response.url()}`);
  }
});

async function switchUser(auth, route = "/app/projects") {
  await page.goto(`${baseUrl}/login`, { waitUntil: "domcontentloaded" });
  await page.evaluate((session) => {
    localStorage.setItem("accessToken", session.accessToken);
    localStorage.setItem("refreshToken", session.refreshToken);
    localStorage.setItem("user", JSON.stringify(session.user));
  }, auth);
  await page.goto(`${baseUrl}${route}`, { waitUntil: "networkidle" });
}

try {
  await switchUser(owner);
  await page.getByLabel("Tên dự án").fill(projectName);
  await page.getByLabel("Mô tả").fill("Luồng cộng tác UI");
  await page.getByRole("button", { name: "Tạo dự án" }).click();
  await page.waitForURL("**/app/projects/*");
  await page.getByRole("heading", { name: projectName, level: 1 }).waitFor();
  const projectId = page.url().split("/").at(-1);
  check(Boolean(projectId), "OWNER tạo dự án trên giao diện");

  await page.getByLabel("Email thành viên").fill(memberEmail.toUpperCase());
  await page.getByRole("button", { name: "Gửi lời mời" }).click();
  await page.getByText("Đã tạo lời mời cho", { exact: false }).waitFor();
  check((await page.getByText(memberEmail, { exact: false }).count()) > 0,
    "OWNER mời thành viên không phân biệt chữ hoa/thường");
  await page.screenshot({ path: path.join(artifacts, "owner-project-desktop.png"), fullPage: true });

  await switchUser(member);
  await page.getByRole("button", { name: "Tham gia" }).click();
  await page.waitForURL(`**/app/projects/${projectId}`);
  await page.getByRole("heading", { name: projectName, level: 1 }).waitFor();
  check((await page.getByRole("heading", { name: "Quản lý dự án" }).count()) === 0,
    "MEMBER không thấy quyền quản lý dự án");
  check((await page.getByLabel("Email thành viên").count()) === 0,
    "MEMBER không thấy form mời người");

  await switchUser(owner, `/app/projects/${projectId}`);
  await page.getByLabel("Tên công việc").fill(taskTitle);
  await page.getByLabel("Người thực hiện").selectOption(member.user.id);
  await page.getByRole("button", { name: "Thêm công việc" }).click();
  await page.getByRole("heading", { name: taskTitle }).waitFor();
  check((await page.getByText(memberEmail, { exact: true }).count()) >= 1,
    "OWNER giao công việc cho MEMBER trên giao diện");

  await switchUser(member, `/app/projects/${projectId}`);
  await page.getByRole("heading", { name: taskTitle }).waitFor();
  await page.getByLabel(`Trạng thái ${taskTitle}`).selectOption("COMPLETED");
  await page.waitForFunction(() =>
    document.querySelectorAll(".team-stats .panel strong")[2]?.textContent?.trim() === "1");
  check((await page.locator(".team-stats .panel strong").nth(3).textContent())?.trim() === "100%",
    "MEMBER hoàn thành task và tiến độ dự án cập nhật 100%");

  await page.setViewportSize({ width: 390, height: 844 });
  await page.reload({ waitUntil: "networkidle" });
  await page.screenshot({ path: path.join(artifacts, "member-project-mobile.png"), fullPage: true });
  const mobileLayout = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    viewportWidth: window.innerWidth,
    overflowing: [...document.querySelectorAll("body *")]
      .filter((element) => element.getBoundingClientRect().right > window.innerWidth + 1)
      .slice(0, 8)
      .map((element) => ({
        tag: element.tagName,
        className: typeof element.className === "string" ? element.className : "",
        width: Math.round(element.getBoundingClientRect().width),
        right: Math.round(element.getBoundingClientRect().right),
      })),
  }));
  check(mobileLayout.scrollWidth <= mobileLayout.viewportWidth + 1,
    `Trang dự án nhóm không tràn ngang trên mobile: ${JSON.stringify(mobileLayout)}`);
  for (const width of [320, 768]) {
    await page.setViewportSize({ width, height: 844 });
    await page.reload({ waitUntil: "networkidle" });
    const scrollWidth = await page.evaluate(() => document.documentElement.scrollWidth);
    check(scrollWidth <= width + 1,
      `Trang dự án nhóm không tràn ngang tại ${width}px (scrollWidth=${scrollWidth})`);
  }
  check(runtimeErrors.length === 0, `Không có lỗi runtime trình duyệt: ${runtimeErrors.join(" | ")}`);

  const report = { passed: true, baseUrl, assertions, runtimeErrors, completedAt: new Date().toISOString() };
  await fs.writeFile(path.join(artifacts, "report.json"), JSON.stringify(report, null, 2));
  console.log(JSON.stringify(report, null, 2));
} catch (error) {
  const report = {
    passed: false, baseUrl, assertions, runtimeErrors,
    error: error instanceof Error ? error.stack : String(error),
    completedAt: new Date().toISOString(),
  };
  await fs.writeFile(path.join(artifacts, "report.json"), JSON.stringify(report, null, 2));
  throw error;
} finally {
  await browser.close();
}
