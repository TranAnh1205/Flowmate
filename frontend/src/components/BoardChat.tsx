import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Send } from "lucide-react";
import { api, getApiErrorMessage } from "../api";
import { useAuth } from "../auth";
import type { BoardMessage } from "../types";

export default function BoardChat({
  projectId,
  boardId,
  canWrite,
}: {
  projectId: string;
  boardId: string;
  canWrite: boolean;
}) {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const [body, setBody] = useState("");
  const key = ["board-messages", projectId, boardId] as const;
  const path = `/projects/${projectId}/boards/${boardId}/messages`;
  const { data = [], error } = useQuery({
    queryKey: key,
    queryFn: async () => (await api.get<BoardMessage[]>(path, { params: { limit: 50 } })).data,
    refetchInterval: 3000,
  });
  const send = useMutation({
    mutationFn: (value: string) => api.post(path, { body: value }),
    onSuccess: () => {
      setBody("");
      queryClient.invalidateQueries({ queryKey: key });
    },
  });
  return (
    <section className="panel team-board-chat">
      <h2>Trò chuyện trong bảng</h2>
      {(error || send.error) && (
        <p className="form-error" role="alert">
          {getApiErrorMessage(error || send.error, "Không thể tải hoặc gửi tin nhắn.")}
        </p>
      )}
      <div className="team-board-chat-messages" aria-live="polite">
        {data.length === 0 && <p>Chưa có tin nhắn.</p>}
        {[...data].reverse().map((message) => (
          <div
            key={message.id}
            className={message.authorId === user?.id ? "mine" : ""}
          >
            <small>
              {message.authorId === user?.id ? "Bạn" : message.authorId.slice(0, 8)}
              {" · "}{new Date(message.createdAt).toLocaleString("vi-VN")}
            </small>
            <p>{message.body}</p>
          </div>
        ))}
      </div>
      {canWrite && (
        <form onSubmit={(event: FormEvent) => {
          event.preventDefault();
          if (body.trim()) send.mutate(body.trim());
        }}>
          <input
            aria-label="Tin nhắn mới"
            value={body}
            onChange={(event) => setBody(event.target.value)}
            maxLength={5000}
            placeholder="Nhắn cho nhóm..."
          />
          <button className="primary" disabled={!body.trim() || send.isPending}>
            <Send size={16} /> Gửi
          </button>
        </form>
      )}
    </section>
  );
}
