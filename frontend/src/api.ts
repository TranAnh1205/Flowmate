import axios from "axios";
import type { AuthResponse } from "./types";

export const api = axios.create({
  baseURL: "/api/v1",
  headers: { "Content-Type": "application/json" },
});

api.interceptors.request.use((c) => {
  const token = localStorage.getItem("accessToken");
  if (token) c.headers.Authorization = `Bearer ${token}`;
  return c;
});

let refreshPromise: Promise<AuthResponse> | null = null;

function clearSession() {
  localStorage.removeItem("accessToken");
  localStorage.removeItem("refreshToken");
  localStorage.removeItem("user");
}

function refreshSession(refreshToken: string) {
  if (!refreshPromise) {
    refreshPromise = axios
      .post<AuthResponse>("/api/v1/auth/refresh-token", { refreshToken })
      .then(({ data }) => data)
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
}

api.interceptors.response.use(
  (r) => r,
  async (error) => {
    const original = error.config;
    if (error.response?.status === 401 && !original?._retry) {
      original._retry = true;
      const refreshToken = localStorage.getItem("refreshToken");
      if (!refreshToken) {
        clearSession();
        location.href = "/login";
        return Promise.reject(error);
      }
      try {
        const data = await refreshSession(refreshToken);
        localStorage.setItem("accessToken", data.accessToken);
        localStorage.setItem("refreshToken", data.refreshToken);
        original.headers.Authorization = `Bearer ${data.accessToken}`;
        return api(original);
      } catch (e) {
        clearSession();
        location.href = "/login";
        return Promise.reject(e);
      }
    }
    return Promise.reject(error);
  },
);

export function getApiErrorMessage(error: unknown, fallback: string) {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as
      { message?: string; details?: Record<string, string> } | undefined;
    if (error.response?.status === 429) {
      const retryAfter =
        data?.details?.retryAfterSeconds ?? error.response.headers?.["retry-after"];
      const suffix = retryAfter ? ` Vui lòng thử lại sau ${retryAfter} giây.` : "";
      return `${data?.message ?? "Bạn đã gửi quá nhiều yêu cầu."}${suffix}`;
    }
    const detail = data?.details && Object.values(data.details)[0];
    if (detail) return detail;
    if (data?.message) return data.message;
  }
  return error instanceof Error && error.message ? error.message : fallback;
}
