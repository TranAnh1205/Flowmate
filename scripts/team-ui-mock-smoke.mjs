import { createServer } from "node:http";
import { randomUUID } from "node:crypto";
import { readFile } from "node:fs/promises";
import path from "node:path";
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";

// Browser/UI contract test only. The real PostgreSQL/Gateway E2E remains separate.
const sourceRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const distRoot = path.join(sourceRoot, "frontend", "dist");
const users = new Map();
const invitations = new Map();
const members = new Map();
const tasks = [];
let project;

function sendJson(response, status, value) {
  response.writeHead(status, { "Content-Type": "application/json; charset=utf-8" });
  response.end(JSON.stringify(value));
}

function account(request) {
  return users.get(request.headers.authorization?.replace(/^Bearer /, ""));
}

function projectResponse(userId) {
  return {
    ...project,
    myRole: members.get(userId)?.role,
    memberCount: members.size,
    taskCount: tasks.length,
    completedTaskCount: tasks.filter((task) => task.status === "COMPLETED").length,
  };
}

async function body(request) {
  let raw = "";
  for await (const chunk of request) raw += chunk;
  return raw ? JSON.parse(raw) : {};
}

async function handleApi(request, response, url) {
  const route = url.pathname.slice("/api/v1".length);
  if (request.method === "POST" && route === "/auth/register") {
    const payload = await body(request);
    const id = randomUUID();
    const user = {
      id, fullName: payload.fullName, email: payload.email.toLowerCase(),
      timezone: "Asia/Bangkok", role: "USER", status: "ACTIVE",
      createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(),
    };
    const accessToken = `mock-${id}`;
    users.set(accessToken, user);
    return sendJson(response, 201, { accessToken, refreshToken: accessToken,
      tokenType: "Bearer", expiresIn: 3600, user });
  }

  const user = account(request);
  if (!user) return sendJson(response, 401, { message: "Unauthenticated" });
  if (request.method === "GET" && route === "/notifications/unread-count") {
    return sendJson(response, 200, { unreadCount: 0 });
  }
  if (request.method === "GET" && route === "/users/me/settings") {
    return sendJson(response, 200, { theme: "system" });
  }
  if (request.method === "GET" && route === "/projects") {
    return sendJson(response, 200, project && members.has(user.id)
      ? [projectResponse(user.id)] : []);
  }
  if (request.method === "POST" && route === "/projects") {
    const payload = await body(request);
    project = {
      id: randomUUID(), name: payload.name, description: payload.description,
      status: "ACTIVE", ownerId: user.id, createdAt: new Date().toISOString(),
    };
    members.set(user.id, { userId: user.id, email: user.email,
      role: "OWNER", joinedAt: new Date().toISOString() });
    return sendJson(response, 201, projectResponse(user.id));
  }
  if (request.method === "GET" && route === "/projects/invitations") {
    return sendJson(response, 200, [...invitations.values()].filter(
      (invitation) => invitation.invitedEmail === user.email && !invitation.accepted));
  }
  if (request.method === "POST" && route === "/projects/invitations/accept") {
    const payload = await body(request);
    const invitation = invitations.get(payload.invitationId);
    if (!invitation || invitation.invitedEmail !== user.email || invitation.accepted) {
      return sendJson(response, 404, { message: "Invitation not found" });
    }
    invitation.accepted = true;
    members.set(user.id, { userId: user.id, email: user.email,
      role: "MEMBER", joinedAt: new Date().toISOString() });
    return sendJson(response, 200, projectResponse(user.id));
  }

  const match = route.match(/^\/projects\/([^/]+)(?:\/(.*))?$/);
  if (!match || !project || match[1] !== project.id || !members.has(user.id)) {
    return sendJson(response, 404, { message: "Project not found" });
  }
  const subroute = match[2] ?? "";
  if (request.method === "GET" && !subroute) {
    return sendJson(response, 200, projectResponse(user.id));
  }
  if (request.method === "GET" && subroute === "members") {
    return sendJson(response, 200, [...members.values()]);
  }
  if (request.method === "POST" && subroute === "invitations") {
    const payload = await body(request);
    const invitation = {
      id: randomUUID(), projectId: project.id, projectName: project.name,
      invitedEmail: payload.email.trim().toLowerCase(),
      expiresAt: new Date(Date.now() + 7 * 86_400_000).toISOString(),
    };
    invitations.set(invitation.id, invitation);
    return sendJson(response, 201, invitation);
  }
  if (request.method === "GET" && subroute === "tasks") {
    return sendJson(response, 200, { content: tasks, page: 0, size: 20,
      totalElements: tasks.length, totalPages: tasks.length ? 1 : 0 });
  }
  if (request.method === "POST" && subroute === "tasks") {
    const payload = await body(request);
    const task = {
      id: randomUUID(), projectId: project.id, title: payload.title,
      description: payload.description, status: payload.status ?? "TODO",
      priority: payload.priority ?? "MEDIUM", createdBy: user.id,
      assigneeId: payload.assigneeId ?? user.id, dueAt: payload.dueAt,
      completedAt: null, version: 0,
      createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(),
    };
    tasks.push(task);
    return sendJson(response, 201, task);
  }
  const statusMatch = subroute.match(/^tasks\/([^/]+)\/status$/);
  if (request.method === "PATCH" && statusMatch) {
    const task = tasks.find((item) => item.id === statusMatch[1]);
    if (!task) return sendJson(response, 404, { message: "Task not found" });
    const payload = await body(request);
    task.status = payload.status;
    task.completedAt = payload.status === "COMPLETED" ? new Date().toISOString() : null;
    task.version += 1;
    task.updatedAt = new Date().toISOString();
    return sendJson(response, 200, task);
  }
  return sendJson(response, 404, { message: `Unmocked API: ${route}` });
}

async function handleStatic(response, url) {
  const pathname = url.pathname;
  const file = pathname.startsWith("/assets/")
    ? path.resolve(distRoot, `.${pathname}`)
    : path.join(distRoot, "index.html");
  if (!file.startsWith(`${distRoot}${path.sep}`)) {
    return sendJson(response, 400, { message: "Invalid path" });
  }
  try {
    const content = await readFile(file);
    const contentType = file.endsWith(".js") ? "text/javascript"
      : file.endsWith(".css") ? "text/css" : "text/html";
    response.writeHead(200, { "Content-Type": `${contentType}; charset=utf-8` });
    response.end(content);
  } catch {
    sendJson(response, 404, { message: "Asset not found" });
  }
}

const server = createServer((request, response) => {
  const url = new URL(request.url, "http://127.0.0.1");
  const handler = url.pathname.startsWith("/api/v1/")
    ? handleApi(request, response, url) : handleStatic(response, url);
  Promise.resolve(handler).catch((error) =>
    sendJson(response, 500, { message: error.message }));
});
await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
const port = server.address().port;
const smoke = path.join(sourceRoot, "scripts", "team-ui-smoke.mjs");
const exitCode = await new Promise((resolve, reject) => {
  const child = spawn(process.execPath, [smoke], {
    cwd: sourceRoot,
    env: {
      ...process.env,
      BASE_URL: `http://127.0.0.1:${port}`,
      TEAM_UI_ARTIFACTS: path.resolve(sourceRoot, "artifacts", "team-ui-mock-smoke"),
    },
    stdio: "inherit",
  });
  child.on("error", reject);
  child.on("exit", (code) => resolve(code ?? 1));
});
await new Promise((resolve) => server.close(resolve));
process.exitCode = exitCode;
