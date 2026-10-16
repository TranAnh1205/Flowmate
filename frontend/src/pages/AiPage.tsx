import { useState } from "react";
import {
  ArrowRight,
  BrainCircuit,
  CalendarClock,
  Check,
  Layers3,
  Sparkles,
  Trash2,
  WandSparkles,
} from "lucide-react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api, getApiErrorMessage } from "../api";

type SuggestedSubtask = {
  title: string;
  estimatedMinutes?: number;
  position: number;
};
type Parsed = {
  title: string;
  description?: string;
  priority: "LOW" | "MEDIUM" | "HIGH" | "URGENT";
  startAt?: string;
  dueAt?: string;
  estimatedMinutes?: number;
  categoryName?: string;
  reminderMinutesBefore?: number;
  subtasks: SuggestedSubtask[];
  warnings: string[];
};
type DailyPlan = {
  date: string;
  schedule: {
    taskId: string;
    title: string;
    startAt: string;
    endAt: string;
    reason: string;
  }[];
  unscheduledTasks: string[];
  warnings: string[];
};
type WeeklySummary = {
  summary: string;
  totalTasks: number;
  completedTasks: number;
  overdueTasks: number;
  completionRate: number;
  strengths: string[];
  suggestions: string[];
};

const priorityOptions: {
  value: Parsed["priority"];
  label: string;
}[] = [
  { value: "LOW", label: "Thấp" },
  { value: "MEDIUM", label: "Trung bình" },
  { value: "HIGH", label: "Cao" },
  { value: "URGENT", label: "Khẩn cấp" },
];

const priorityLabel = (priority: Parsed["priority"]) =>
  priorityOptions.find((item) => item.value === priority)?.label ?? priority;

const currentLocalDate = () => {
  const now = new Date();
  return new Date(now.getTime() - now.getTimezoneOffset() * 60_000)
    .toISOString()
    .slice(0, 10);
};

export default function AiPage() {
  const [prompt, setPrompt] = useState("");
  const [result, setResult] = useState<Parsed | null>(null);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [message, setMessage] = useState("");
  const [planDate, setPlanDate] = useState(currentLocalDate);
  const queryClient = useQueryClient();

  const applyResult = (value: Parsed) => {
    setResult(value);
    setSelected(new Set(value.subtasks.map((_item, index) => index)));
    setMessage("");
  };

  const parse = useMutation({
    mutationFn: async () =>
      (await api.post<Parsed>("/ai/parse-task", { prompt })).data,
    onSuccess: applyResult,
  });
  const breakdown = useMutation({
    mutationFn: async () =>
      (
        await api.post<{ subtasks: SuggestedSubtask[] }>("/ai/breakdown-task", {
          title: result?.title,
          description: result?.description,
          maximumSubtasks: 6,
        })
      ).data,
    onSuccess: ({ subtasks }) => result && applyResult({ ...result, subtasks }),
  });
  const create = useMutation({
    mutationFn: () => {
      if (!result) throw new Error("Không có bản xem trước để tạo.");
      return api.post("/tasks", {
        title: result.title,
        description: result.description || null,
        status: "TODO",
        priority: result.priority,
        startAt: result.startAt || null,
        dueAt: result.dueAt || null,
        estimatedMinutes: result.estimatedMinutes || null,
        reminderMinutesBefore: result.reminderMinutesBefore ?? null,
        tagIds: [],
        subtasks: result.subtasks
          .filter((_item, index) => selected.has(index))
          .map((item) => ({ title: item.title })),
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["tasks"] });
      queryClient.invalidateQueries({ queryKey: ["stats"] });
      setMessage("Công việc đã được tạo thành công.");
      setResult(null);
      setSelected(new Set());
      setPrompt("");
    },
  });
  const dailyPlan = useMutation({
    mutationFn: async () =>
      (await api.post<DailyPlan>("/ai/daily-plan", { date: planDate })).data,
  });
  const weeklySummary = useMutation({
    mutationFn: async () =>
      (await api.post<WeeklySummary>("/ai/weekly-summary")).data,
  });

  const updateSubtask = (index: number, title: string) =>
    result &&
    setResult({
      ...result,
      subtasks: result.subtasks.map((item, itemIndex) =>
        itemIndex === index ? { ...item, title } : item,
      ),
    });
  const removeSubtask = (index: number) => {
    if (!result) return;
    setResult({
      ...result,
      subtasks: result.subtasks.filter(
        (_item, itemIndex) => itemIndex !== index,
      ),
    });
    setSelected(
      new Set(
        [...selected]
          .filter((itemIndex) => itemIndex !== index)
          .map((itemIndex) => (itemIndex > index ? itemIndex - 1 : itemIndex)),
      ),
    );
  };
  const toggleSelected = (index: number) =>
    setSelected((current) => {
      const next = new Set(current);
      if (next.has(index)) next.delete(index);
      else next.add(index);
      return next;
    });

  const examples = [
    "Tối mai học Spring Boot trong 2 tiếng, ưu tiên cao",
    "Chuẩn bị bài thuyết trình trước thứ Sáu",
    "Chia nhỏ việc hoàn thành đồ án trong tuần này",
  ];

  return (
    <>
      <div className="page-heading">
        <div>
          <span className="eyebrow">TRỢ LÝ CÁ NHÂN</span>
          <h1>Flowmate AI</h1>
          <p>
            Nói điều bạn muốn hoàn thành. AI sẽ giúp biến nó thành bước đi rõ
            ràng.
          </p>
        </div>
      </div>
      <div className="ai-page-grid">
        <section className="panel ai-workspace">
          <div className="ai-chat-head">
            <div className="ai-avatar">
              <Sparkles />
            </div>
            <div>
              <h2>Tôi có thể giúp gì cho bạn?</h2>
              <p>Mô tả tự nhiên bằng tiếng Việt.</p>
            </div>
          </div>
          <div className="prompt-examples">
            {examples.map((example) => (
              <button key={example} onClick={() => setPrompt(example)}>
                {example}
              </button>
            ))}
          </div>
          <textarea
            rows={6}
            value={prompt}
            onChange={(event) => setPrompt(event.target.value)}
            placeholder="Ví dụ: Tối mai lúc 8 giờ học Spring Boot trong 2 tiếng, nhắc trước 30 phút..."
          />
          <button
            className="primary ai-send"
            disabled={!prompt.trim() || parse.isPending}
            onClick={() => parse.mutate()}
          >
            {parse.isPending ? "Đang phân tích..." : "Phân tích yêu cầu"}
            <ArrowRight size={18} />
          </button>
          {parse.isError && (
            <div className="form-error ai-status">
              {getApiErrorMessage(parse.error, "Không thể phân tích yêu cầu. Vui lòng thử lại.")}
            </div>
          )}
          {message && (
            <div className="success-message">
              <Check />
              {message}
            </div>
          )}
        </section>
        <aside className="ai-capabilities">
          <div>
            <BrainCircuit />
            <h3>Tạo công việc có cấu trúc</h3>
            <p>Tự nhận diện tiêu đề, độ ưu tiên, thời lượng và lời nhắc.</p>
          </div>
          <div>
            <Layers3 />
            <h3>Chia nhỏ mục tiêu</h3>
            <p>Biến việc lớn thành những bước đủ nhỏ để bắt đầu.</p>
          </div>
          <div>
            <CalendarClock />
            <h3>Lập kế hoạch ngày</h3>
            <p>Xếp việc theo deadline và thời gian bạn có.</p>
          </div>
        </aside>
      </div>
      <div className="ai-insight-grid">
        <section className="panel ai-insight-panel">
          <div className="panel-heading">
            <div>
              <span className="eyebrow">LỊCH TẬP TRUNG</span>
              <h2>Lập kế hoạch trong ngày</h2>
            </div>
            <CalendarClock />
          </div>
          <div className="ai-plan-actions">
            <label>
              Ngày cần lập kế hoạch
              <input
                type="date"
                value={planDate}
                onChange={(event) => setPlanDate(event.target.value)}
              />
            </label>
            <button
              className="primary"
              onClick={() => dailyPlan.mutate()}
              disabled={!planDate || dailyPlan.isPending}
            >
              {dailyPlan.isPending ? "Đang sắp xếp..." : "Tạo kế hoạch ngày"}
            </button>
          </div>
          {dailyPlan.isError && (
            <p className="form-error">
              {getApiErrorMessage(dailyPlan.error, "Không thể lập kế hoạch cho ngày này.")}
            </p>
          )}
          {dailyPlan.data && (
            <div className="ai-plan-result">
              {!dailyPlan.data.schedule.length && (
                <p>Không có công việc phù hợp để xếp lịch trong ngày này.</p>
              )}
              {dailyPlan.data.schedule.map((item) => (
                <article key={item.taskId}>
                  <strong>{item.title}</strong>
                  <span>
                    {item.startAt.slice(0, 5)} – {item.endAt.slice(0, 5)}
                  </span>
                  <small>{item.reason}</small>
                </article>
              ))}
              {!!dailyPlan.data.unscheduledTasks.length && (
                <p>
                  {dailyPlan.data.unscheduledTasks.length} công việc chưa thể
                  xếp lịch.
                </p>
              )}
              {dailyPlan.data.warnings.map((warning) => (
                <p className="ai-warning" key={warning}>
                  {warning}
                </p>
              ))}
            </div>
          )}
        </section>
        <section className="panel ai-insight-panel">
          <div className="panel-heading">
            <div>
              <span className="eyebrow">NHÌN LẠI TUẦN</span>
              <h2>Tổng kết tiến độ</h2>
            </div>
            <BrainCircuit />
          </div>
          <button
            className="primary"
            onClick={() => weeklySummary.mutate()}
            disabled={weeklySummary.isPending}
          >
            {weeklySummary.isPending ? "Đang tổng hợp..." : "Tạo tổng kết tuần"}
          </button>
          {weeklySummary.isError && (
            <p className="form-error">
              {getApiErrorMessage(weeklySummary.error, "Không thể tạo tổng kết tuần.")}
            </p>
          )}
          {weeklySummary.data && (
            <div className="ai-summary-result">
              <p>{weeklySummary.data.summary}</p>
              <div className="ai-summary-stats">
                <span>
                  <strong>{weeklySummary.data.totalTasks}</strong> Tổng việc
                </span>
                <span>
                  <strong>{weeklySummary.data.completedTasks}</strong> Hoàn
                  thành
                </span>
                <span>
                  <strong>{weeklySummary.data.overdueTasks}</strong> Quá hạn
                </span>
                <span>
                  <strong>
                    {Math.round(weeklySummary.data.completionRate)}%
                  </strong>{" "}
                  Tỷ lệ
                </span>
              </div>
              {!!weeklySummary.data.strengths.length && (
                <div>
                  <h3>Điểm tốt</h3>
                  <ul>
                    {weeklySummary.data.strengths.map((item) => (
                      <li key={item}>{item}</li>
                    ))}
                  </ul>
                </div>
              )}
              {!!weeklySummary.data.suggestions.length && (
                <div>
                  <h3>Gợi ý tiếp theo</h3>
                  <ul>
                    {weeklySummary.data.suggestions.map((item) => (
                      <li key={item}>{item}</li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          )}
        </section>
      </div>
      {result && (
        <section className="panel ai-preview">
          <div className="panel-heading">
            <div>
              <span className="eyebrow">BẢN XEM TRƯỚC</span>
              <h2>AI đã hiểu yêu cầu như sau</h2>
            </div>
            <div className="priority-control">
              <label htmlFor="ai-priority">Mức ưu tiên</label>
              <select
                id="ai-priority"
                aria-label="Chọn mức ưu tiên"
                className={`priority-select priority-${result.priority.toLowerCase()}`}
                value={result.priority}
                onChange={(event) =>
                  setResult({
                    ...result,
                    priority: event.target.value as Parsed["priority"],
                  })
                }
              >
                {priorityOptions.map((option) => (
                  <option value={option.value} key={option.value}>
                    {option.label} ({option.value})
                  </option>
                ))}
              </select>
              <span
                className={`priority-feedback priority-${result.priority.toLowerCase()}`}
                role="status"
                aria-live="polite"
              >
                <Check size={14} /> Đã chọn: {priorityLabel(result.priority)}
              </span>
            </div>
          </div>
          <div className="preview-grid">
            <label>
              Tiêu đề
              <input
                value={result.title}
                onChange={(event) =>
                  setResult({ ...result, title: event.target.value })
                }
              />
            </label>
            <label>
              Thời gian dự kiến
              <input
                type="number"
                min={1}
                value={result.estimatedMinutes || ""}
                onChange={(event) =>
                  setResult({
                    ...result,
                    estimatedMinutes: Number(event.target.value) || undefined,
                  })
                }
              />
            </label>
          </div>
          {result.warnings?.map((warning) => (
            <p className="ai-warning" key={warning}>
              {warning}
            </p>
          ))}
          <div className="suggested-subtasks">
            <div className="subtask-heading">
              <h3>Các bước đề xuất</h3>
              <button
                className="text-btn"
                onClick={() => breakdown.mutate()}
                disabled={breakdown.isPending}
              >
                <Layers3 size={15} />
                {breakdown.isPending
                  ? "Đang chia nhỏ..."
                  : "AI chia nhỏ công việc"}
              </button>
            </div>
            {result.subtasks.map((subtask, index) => (
              <div
                className="suggested-row"
                key={`${index}-${subtask.position}`}
              >
                <input
                  type="checkbox"
                  checked={selected.has(index)}
                  onChange={() => toggleSelected(index)}
                  aria-label="Chọn bước"
                />
                <input
                  value={subtask.title}
                  onChange={(event) => updateSubtask(index, event.target.value)}
                />
                <button
                  className="icon-btn"
                  onClick={() => removeSubtask(index)}
                  aria-label="Xóa bước"
                >
                  <Trash2 size={15} />
                </button>
              </div>
            ))}
            {!result.subtasks.length && (
              <p>
                Chưa có bước con. Chọn “AI chia nhỏ công việc” để nhận đề xuất.
              </p>
            )}
            {breakdown.isError && (
              <p className="form-error">
                {getApiErrorMessage(breakdown.error, "Không thể chia nhỏ công việc lúc này.")}
              </p>
            )}
          </div>
          {create.isError && (
            <p className="form-error">
              Không thể tạo công việc từ bản xem trước.
            </p>
          )}
          <div className="modal-actions">
            <button className="ghost" onClick={() => setResult(null)}>
              Bỏ qua
            </button>
            <button
              className="primary"
              onClick={() => create.mutate()}
              disabled={create.isPending || !result.title.trim()}
            >
              <WandSparkles size={17} />
              {create.isPending ? "Đang tạo..." : "Xác nhận tạo công việc"}
            </button>
          </div>
        </section>
      )}
    </>
  );
}
