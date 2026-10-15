import fs from "node:fs/promises";
import path from "node:path";
import { createRequire } from "node:module";

const baseUrl = process.env.BASE_URL ?? "http://127.0.0.1:3000";
const adminEmail = process.env.ADMIN_EMAIL;
const adminPassword = process.env.ADMIN_PASSWORD;
const nodeModules = process.env.CODEX_NODE_MODULES;
const edgePath =
  process.env.EDGE_PATH ??
  "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe";

if (!adminEmail || !adminPassword || !nodeModules) {
  throw new Error("ADMIN_EMAIL, ADMIN_PASSWORD and CODEX_NODE_MODULES are required");
}

const require = createRequire(import.meta.url);
const { chromium } = require(path.join(nodeModules, "playwright"));
const stamp = `${Date.now()}-${Math.random().toString(16).slice(2)}`;
const regularEmail = `ui-smoke-${stamp}@smarttask.local`;
const regularPassword = "UiSmoke@123";
const artifacts = path.resolve("..", "artifacts", "ui-smoke");

function check(condition, message) {
  if (!condition) throw new Error(message);
}

async function jsonRequest(requestPath, options = {}) {
  const response = await fetch(`${baseUrl}${requestPath}`, {
    ...options,
    headers: { "Content-Type": "application/json", ...(options.headers ?? {}) },
  });
  const text = await response.text();
  const data = text ? JSON.parse(text) : null;
  if (!response.ok) {
    throw new Error(`${options.method ?? "GET"} ${requestPath}: ${response.status} ${text}`);
  }
  return data;
}

await fs.mkdir(artifacts, { recursive: true });
const regular = await jsonRequest("/api/v1/auth/register", {
  method: "POST",
  body: JSON.stringify({
    fullName: "UI Smoke User",
    email: regularEmail,
    password: regularPassword,
  }),
});

const browser = await chromium.launch({ executablePath: edgePath, headless: true });
const page = await browser.newPage({
  viewport: { width: 1440, height: 1000 },
  colorScheme: "dark",
});
const runtimeErrors = [];

page.on("pageerror", (error) => runtimeErrors.push(`pageerror: ${error.message}`));
page.on("console", (message) => {
  if (message.type() === "error") runtimeErrors.push(`console: ${message.text()}`);
});
page.on("requestfailed", (request) => {
  runtimeErrors.push(
    `requestfailed: ${request.method()} ${request.url()} ${request.failure()?.errorText ?? "unknown"}`,
  );
});

const assertions = [];
async function assertUi(name, predicate) {
  const value = await predicate();
  check(value, name);
  assertions.push(name);
}

try {
  await page.goto(`${baseUrl}/login`, { waitUntil: "networkidle" });
  const loginColors = await page.evaluate(() => {
    const panel = getComputedStyle(document.querySelector(".auth-panel"));
    const heading = getComputedStyle(document.querySelector(".auth-panel h2"));
    const input = getComputedStyle(document.querySelector(".auth-panel input"));
    return {
      theme: document.documentElement.dataset.theme,
      panelBackground: panel.backgroundColor,
      headingColor: heading.color,
      inputBackground: input.backgroundColor,
      inputColor: input.color,
    };
  });
  await assertUi(
    "Form đăng nhập giữ tương phản sáng khi hệ thống dùng dark mode",
    async () =>
      loginColors.theme === "dark" &&
      loginColors.panelBackground === "rgb(255, 255, 255)" &&
      loginColors.headingColor === "rgb(23, 32, 51)" &&
      loginColors.inputColor !== loginColors.inputBackground,
  );
  await page.locator('input[type="email"]').fill(adminEmail);
  await page.locator('input[type="password"]').fill(adminPassword);
  await page.getByRole("button", { name: /Đăng nhập/ }).click();
  await page.waitForURL("**/admin/dashboard", { timeout: 15_000 });
  await page.locator(".admin-stat-card").first().waitFor();
  await page.getByText("Tất cả service hoạt động").waitFor({ timeout: 15_000 });
  await page.waitForFunction(() =>
    [...document.querySelectorAll(".admin-stat-card strong")].every(
      (element) => element.textContent?.trim() !== "—",
    ),
  );

  await assertUi("Admin đăng nhập qua giao diện và CORS thành công", async () =>
    page.url().endsWith("/admin/dashboard"),
  );
  await assertUi("Dashboard hiển thị đủ 8 thẻ thống kê", async () =>
    (await page.locator(".admin-stat-card").count()) === 8,
  );
  await assertUi("Dashboard hiển thị đủ 4 microservice", async () =>
    (await page.locator(".service-list > div").count()) === 4,
  );
  await assertUi("Dashboard desktop không tràn ngang", async () =>
    page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1),
  );
  await page.screenshot({
    path: path.join(artifacts, "admin-dashboard-desktop.png"),
    fullPage: true,
  });

  await page.goto(`${baseUrl}/app/tasks/today`, { waitUntil: "networkidle" });
  await page.getByRole("heading", { name: "Hôm nay" }).waitFor();
  await assertUi(
    "Sidebar chỉ đánh dấu Hôm nay tại route /app/tasks/today",
    async () => {
      const activeLabels = await page
        .locator(".sidebar nav a.active span")
        .allTextContents();
      return (
        activeLabels.length === 1 && activeLabels[0].trim() === "Hôm nay"
      );
    },
  );
  await page.goto(`${baseUrl}/admin/dashboard`, { waitUntil: "networkidle" });
  await page.getByText("Tất cả service hoạt động").waitFor({ timeout: 15_000 });

  await page.getByRole("link", { name: "Người dùng" }).click();
  await page.waitForURL("**/admin/users");
  await page.getByLabel("Tìm người dùng").fill(regularEmail);
  await page.getByRole("button", { name: "Tìm kiếm" }).click();
  await page.getByText(regularEmail, { exact: true }).waitFor();
  await assertUi("Trang người dùng có tìm kiếm và 2 bộ lọc", async () =>
    (await page.locator(".admin-filter-panel select").count()) === 2,
  );

  const row = page.locator("tr", { hasText: regularEmail });
  await row.getByRole("button", { name: "Xem chi tiết" }).click();
  await page.waitForURL("**/admin/users/*");
  await page.getByText(regularEmail, { exact: true }).waitFor();
  const revokeButton = page.getByRole("button", { name: "Thu hồi", exact: true });
  await revokeButton.waitFor();
  await assertUi("Chi tiết người dùng hiển thị phiên hoạt động", async () =>
    (await revokeButton.count()) === 1,
  );
  await page.screenshot({
    path: path.join(artifacts, "admin-user-detail-desktop.png"),
    fullPage: true,
  });

  await revokeButton.click();
  const dialog = page.getByRole("dialog");
  await dialog.locator("textarea").fill("Kiểm thử thu hồi một phiên từ giao diện");
  await dialog.locator('input[type="password"]').fill(adminPassword);
  await dialog.getByRole("button", { name: "Xác nhận thao tác" }).click();
  await page.getByText("Thao tác quản trị đã được thực hiện và ghi audit log.").waitFor({
    timeout: 15_000,
  });
  await assertUi("Thu hồi một phiên qua modal xác thực thành công", async () =>
    (await page.getByRole("button", { name: "Thu hồi", exact: true }).count()) === 0,
  );

  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(`${baseUrl}/admin/dashboard`, { waitUntil: "networkidle" });
  await page.getByLabel("Mở menu quản trị").waitFor();
  await assertUi("Dashboard mobile không tràn ngang", async () =>
    page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1),
  );
  await page.getByLabel("Mở menu quản trị").click();
  await page.locator(".admin-sidebar.mobile-open").waitFor();
  await page.waitForFunction(() => {
    const box = document.querySelector(".admin-sidebar.mobile-open")?.getBoundingClientRect();
    return !!box && box.x >= -1;
  });
  await assertUi("Sidebar mobile mở đúng chiều rộng và không lệch màn hình", async () => {
    const box = await page.locator(".admin-sidebar.mobile-open").boundingBox();
    return !!box && box.x >= -1 && box.width >= 250;
  });
  await page.screenshot({
    path: path.join(artifacts, "admin-dashboard-mobile-menu.png"),
    fullPage: true,
  });

  await page.getByRole("button", { name: "Đăng xuất" }).click();
  await page.waitForURL("**/login");
  await page.evaluate((auth) => {
    localStorage.setItem("accessToken", auth.accessToken);
    localStorage.setItem("refreshToken", auth.refreshToken);
    localStorage.setItem("user", JSON.stringify(auth.user));
  }, regular);
  await page.goto(`${baseUrl}/admin/dashboard`, { waitUntil: "networkidle" });
  await page.waitForURL("**/403");
  await assertUi("USER bị chặn khỏi vùng Admin và chuyển tới 403", async () =>
    (await page.getByText("Bạn không thể truy cập khu vực này").count()) === 1,
  );
  await page.screenshot({
    path: path.join(artifacts, "regular-user-forbidden-mobile.png"),
    fullPage: true,
  });

  check(runtimeErrors.length === 0, `Browser runtime errors:\n${runtimeErrors.join("\n")}`);
  assertions.push("Không có console error, page error hoặc network request failure");

  const report = {
    passed: true,
    baseUrl,
    browser: "Microsoft Edge (Playwright/CDP)",
    viewport: ["1440x1000", "390x844"],
    assertions,
    runtimeErrors,
    completedAt: new Date().toISOString(),
  };
  await fs.writeFile(path.join(artifacts, "report.json"), JSON.stringify(report, null, 2));
  console.log(JSON.stringify(report, null, 2));
} catch (error) {
  const report = {
    passed: false,
    baseUrl,
    assertions,
    runtimeErrors,
    error: error instanceof Error ? error.stack : String(error),
    completedAt: new Date().toISOString(),
  };
  await fs.writeFile(path.join(artifacts, "report.json"), JSON.stringify(report, null, 2));
  throw error;
} finally {
  await browser.close();
}
