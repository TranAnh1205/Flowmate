const base = process.env.BASE_URL ?? "http://localhost:8090";
const stamp = Date.now().toString(36);
const password = "BoardE2E@123";

async function call(path, method = "GET", body, token, expected = 200) {
  for (let retry = 0; retry < 6; retry++) {
    const response = await fetch(base + path, {
      method,
      headers: {
        Accept: "application/json",
        ...(body === undefined ? {} : { "Content-Type": "application/json" }),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    const text = await response.text();
    if (response.status === 429 && retry < 5) {
      await new Promise((resolve) => setTimeout(resolve, 13_000));
      continue;
    }
    if (response.status !== expected)
      throw new Error(`${method} ${path}: expected ${expected}, got ${response.status}: ${text}`);
    return text ? JSON.parse(text) : null;
  }
  throw new Error("Rate limit retries exhausted");
}

async function register(kind) {
  const email = `board-${kind}-${stamp}@smarttask.local`;
  const result = await call("/api/v1/auth/register", "POST",
    { fullName: `Board ${kind}`, email, password }, undefined, 201);
  return { email, id: result.user.id, token: result.accessToken };
}

const owner = await register("owner");
const member = await register("member");
const outsider = await register("outsider");
const project = await call("/api/v1/projects", "POST",
  { name: `Board E2E ${stamp}` }, owner.token, 201);
const root = `/api/v1/projects/${project.id}`;
const originalBoards = await call(`${root}/boards`, "GET", undefined, owner.token);
if (originalBoards.length !== 1 || !originalBoards[0].isDefault)
  throw new Error("New project did not get a default board");

const invitation = await call(`${root}/invitations`, "POST",
  { email: member.email }, owner.token, 201);
await call("/api/v1/projects/invitations/accept", "POST",
  { invitationId: invitation.id }, member.token);
const board = await call(`${root}/boards`, "POST",
  { title: "Sprint 1", visibility: "WORKSPACE", background: "indigo" }, owner.token, 201);
const path = `${root}/boards/${board.id}`;
await call(`${path}/view`, "POST", undefined, owner.token, 204);
const recentBoards = await call("/api/v1/projects/recent-boards", "GET", undefined, owner.token);
if (!recentBoards.some((item) => item.board.id === board.id && item.projectName === project.name))
  throw new Error("Recently viewed board was not listed");
if ((await call("/api/v1/projects/recent-boards", "GET", undefined, outsider.token)).length)
  throw new Error("Recently viewed board leaked to another user");
let details = await call(path, "GET", undefined, member.token);
if (details.lists.length !== 4) throw new Error("New board does not have four workflow lists");
await call(path, "GET", undefined, outsider.token, 404);
const customList = await call(`${path}/lists`, "POST",
  { title: "Review" }, member.token, 201);
const renamedList = await call(`${path}/lists/${customList.id}`, "PUT",
  { title: "Kiểm duyệt" }, member.token);
if (renamedList.title !== "Kiểm duyệt") throw new Error("List rename failed");
const listOrder = [...details.lists.map((list) => list.id), customList.id].reverse();
await call(`${path}/lists/order`, "PUT", { listIds: listOrder }, member.token, 204);
const todo = details.lists.find((list) => list.workflowStatus === "TODO");
const done = details.lists.find((list) => list.workflowStatus === "COMPLETED");
const card = await call(`${path}/lists/${todo.id}/cards`, "POST",
  { title: "Viết báo cáo" }, owner.token, 201);
if (card.title !== "Viết báo cáo") throw new Error("Card creation failed");
const moved = await call(`${path}/cards/${card.id}/move`, "PATCH",
  { listId: customList.id, index: 0, version: card.version }, owner.token);
if (moved.version <= card.version) throw new Error("Card version did not advance");
const completed = await call(`${path}/cards/${card.id}/move`, "PATCH",
  { listId: done.id, index: 0, version: moved.version }, owner.token);
if (completed.status !== "COMPLETED") throw new Error("Workflow status did not update");
const edited = await call(`${path}/cards/${card.id}`, "PUT", {
  title: "Viết báo cáo hoàn chỉnh", description: "Đã sửa từ board",
  status: completed.status, priority: completed.priority,
  assigneeId: completed.assigneeId,
  startAt: "2026-11-01T09:00:00Z", dueAt: "2026-11-01T10:00:00Z",
  version: completed.version,
}, owner.token);
if (edited.title !== "Viết báo cáo hoàn chỉnh" ||
    edited.startAt !== "2026-11-01T09:00:00Z") throw new Error("Card edit or start date failed");
if ((await call(`${root}/tasks/${card.id}`, "GET", undefined, owner.token)).startAt !== edited.startAt)
  throw new Error("Team task start date was not persisted");
await call(`${path}/cards/${card.id}`, "PUT", {
  title: edited.title, description: edited.description,
  status: edited.status, priority: edited.priority, assigneeId: edited.assigneeId,
  startAt: "2026-11-02T09:00:00Z", dueAt: "2026-11-01T10:00:00Z",
  version: edited.version,
}, owner.token, 400);
await call(`${path}/cards/${card.id}/move`, "PATCH",
  { listId: todo.id, index: 0, version: moved.version }, owner.token, 409);
await call(`${path}/lists/${customList.id}`, "DELETE", undefined, member.token, 204);
details = await call(path, "GET", undefined, member.token);
if (!details.lists.find((list) => list.id === done.id).cards.some((value) => value.id === card.id))
  throw new Error("Card move was not persisted");
await call(`${root}/boards`, "POST",
  { title: "Private", visibility: "PRIVATE" }, owner.token, 400);
const cardPath = `${path}/cards/${card.id}`;
const comment = await call(`${cardPath}/comments`, "POST",
  { body: "Đã xem thẻ" }, member.token, 201);
if (comment.body !== "Đã xem thẻ") throw new Error("Comment was not stored");
if ((await call(`${cardPath}/comments`, "GET", undefined, owner.token)).length !== 1)
  throw new Error("Comment was not visible to project owner");
await call(`${cardPath}/comments`, "GET", undefined, outsider.token, 404);
const item = await call(`${cardPath}/checklist`, "POST",
  { title: "Kiểm tra nội dung" }, owner.token, 201);
const checked = await call(`${cardPath}/checklist/${item.id}`, "PUT",
  { title: item.title, done: true }, member.token);
if (!checked.done) throw new Error("Checklist update failed");
const label = await call(`${path}/labels`, "POST",
  { name: "Quan trọng", color: "rose" }, owner.token, 201);
await call(`${cardPath}/labels/${label.id}`, "POST", undefined, member.token, 204);
if ((await call(`${cardPath}/labels`, "GET", undefined, owner.token)).length !== 1)
  throw new Error("Label attachment failed");
await call(`${cardPath}/watchers/me`, "PUT", undefined, member.token, 204);
if (!(await call(`${cardPath}/watchers`, "GET", undefined, owner.token)).includes(member.id))
  throw new Error("Watcher was not stored");
const message = await call(`${path}/messages`, "POST",
  { body: "Xin chào cả nhóm" }, member.token, 201);
if (message.body !== "Xin chào cả nhóm") throw new Error("Message was not stored");
if ((await call(`${path}/messages`, "GET", undefined, owner.token)).length !== 1)
  throw new Error("Message was not visible to project owner");
await call(`${path}/messages`, "GET", undefined, outsider.token, 404);
if ((await call(`${path}/events`, "GET", undefined, owner.token)).length < 5)
  throw new Error("Board activity was not recorded");

const uploadResponse = await fetch(base + `${cardPath}/attachments`, {
  method: "POST",
  headers: { Authorization: `Bearer ${owner.token}` },
  body: (() => {
    const form = new FormData();
    form.append("file", new Blob(["Board attachment test"], { type: "text/plain" }), "test.txt");
    return form;
  })(),
});
if (uploadResponse.status !== 201)
  throw new Error(`Attachment upload failed: ${uploadResponse.status} ${await uploadResponse.text()}`);
const attachment = await uploadResponse.json();
const download = await fetch(base + `${cardPath}/attachments/${attachment.id}`, {
  headers: { Authorization: `Bearer ${member.token}` },
});
if (download.status !== 200 || await download.text() !== "Board attachment test" ||
    !download.headers.get("content-disposition")?.includes("attachment"))
  throw new Error("Authorized attachment download failed");
await call(`${cardPath}/attachments/${attachment.id}`, "GET", undefined, outsider.token, 404);
await call(`${path}/copy`, "POST", { title: "Sprint 1 copy" }, member.token, 403);
const copiedBoard = await call(`${path}/copy`, "POST",
  { title: "Sprint 1 copy" }, owner.token, 201);
const copiedPath = `${root}/boards/${copiedBoard.id}`;
const copiedDetails = await call(copiedPath, "GET", undefined, member.token);
if (copiedDetails.lists.length !== 4 ||
    copiedDetails.lists.flatMap((list) => list.cards).length !== 1)
  throw new Error("Board copy did not preserve active lists and cards");
const copiedCard = copiedDetails.lists.flatMap((list) => list.cards)[0];
if (copiedCard.id === card.id || copiedCard.title !== edited.title ||
    copiedCard.startAt !== edited.startAt)
  throw new Error("Board copy did not create an independent card");
if ((await call(`${copiedPath}/cards/${copiedCard.id}/checklist`, "GET", undefined, owner.token)).length !== 1 ||
    (await call(`${copiedPath}/cards/${copiedCard.id}/labels`, "GET", undefined, owner.token)).length !== 1)
  throw new Error("Board copy did not preserve checklist and labels");
if ((await call(`${copiedPath}/cards/${copiedCard.id}/comments`, "GET", undefined, owner.token)).length ||
    (await call(`${copiedPath}/cards/${copiedCard.id}/attachments`, "GET", undefined, owner.token)).length)
  throw new Error("Board copy included conversation or attachment history");
await call(copiedPath, "GET", undefined, outsider.token, 404);
const copy = await call(`${cardPath}/copy`, "POST",
  { targetBoardId: board.id, listId: done.id }, owner.token, 201);
if (!copy.title.includes("bản sao")) throw new Error("Card copy failed");
if ((await call(`${path}/cards/${copy.id}/checklist`, "GET", undefined, owner.token)).length !== 1)
  throw new Error("Checklist was not copied");
await call(`${path}/cards/${copy.id}`, "DELETE", undefined, owner.token, 204);
if (!(await call(`${path}/archived-cards`, "GET", undefined, owner.token))
    .some((value) => value.id === copy.id)) throw new Error("Archive failed");
await call(`${path}/archived-cards/${copy.id}/restore`, "POST", undefined, owner.token);
const destination = await call(`${root}/boards`, "POST",
  { title: "Sprint 2", visibility: "WORKSPACE", template: "sprint" }, owner.token, 201);
const targetDetails = await call(`${root}/boards/${destination.id}`, "GET", undefined, owner.token);
if (targetDetails.lists.length !== 5 ||
    !targetDetails.lists.some((list) => list.title === "Review" && list.workflowStatus === null))
  throw new Error("Sprint board template did not create its lists");
const targetList = targetDetails.lists.find((list) => list.workflowStatus === "TODO");
const transferred = await call(`${cardPath}/transfer`, "POST",
  { targetBoardId: destination.id, listId: targetList.id, version: edited.version }, owner.token);
if (transferred.status !== "TODO") throw new Error("Cross-board transfer did not update status");
if (!(await call(`${root}/boards/${destination.id}`, "GET", undefined, owner.token))
    .lists.some((list) => list.cards.some((value) => value.id === card.id)))
  throw new Error("Cross-board transfer was not persisted");
console.log("Board E2E passed: board templates and copy, recent boards, board/list/card, ACL, comments, checklist, labels, watchers, messages, activity, attachments, card copy, archive and transfer.");
