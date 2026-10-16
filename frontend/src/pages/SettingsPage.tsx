import { useEffect, useState } from "react";
import { KeyRound, Save, ShieldCheck, UserRound } from "lucide-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, getApiErrorMessage } from "../api";
import { useAuth } from "../auth";
import { applyTheme, type ThemePreference } from "../theme";
import type { User } from "../types";

type Settings = {
  workStartTime: string;
  workEndTime: string;
  defaultReminderMinutes: number;
  weekStartDay: number;
  language: string;
  theme: ThemePreference;
};

const timezones = [
  "Asia/Bangkok",
  "Asia/Ho_Chi_Minh",
  "Asia/Singapore",
  "Asia/Tokyo",
  "Europe/London",
  "America/New_York",
];

export default function SettingsPage() {
  const { user, updateUser } = useAuth();
  const queryClient = useQueryClient();
  const [form, setForm] = useState<Settings>({
    workStartTime: "08:00",
    workEndTime: "17:30",
    defaultReminderMinutes: 30,
    weekStartDay: 1,
    language: "vi",
    theme: "system",
  });
  const [profile, setProfile] = useState({
    fullName: user?.fullName || "",
    timezone: user?.timezone || "Asia/Bangkok",
  });
  const [password, setPassword] = useState({
    currentPassword: "",
    newPassword: "",
    confirmation: "",
  });
  const [passwordError, setPasswordError] = useState("");

  const { data } = useQuery({
    queryKey: ["settings"],
    queryFn: async () => (await api.get<Settings>("/users/me/settings")).data,
  });
  useEffect(() => {
    if (data) setForm(data);
  }, [data]);
  useEffect(() => {
    if (user) {
      setProfile({ fullName: user.fullName, timezone: user.timezone });
    }
  }, [user]);

  const saveSettings = useMutation({
    mutationFn: async () =>
      (await api.put<Settings>("/users/me/settings", form)).data,
    onSuccess: (value) => {
      queryClient.setQueryData(["settings"], value);
      applyTheme(value.theme);
    },
  });
  const saveProfile = useMutation({
    mutationFn: async () =>
      (
        await api.put<User>("/users/me", {
          fullName: profile.fullName.trim(),
          timezone: profile.timezone,
          avatarUrl: user?.avatarUrl || null,
        })
      ).data,
    onSuccess: (value) => {
      updateUser(value);
      queryClient.invalidateQueries({ queryKey: ["stats"] });
      queryClient.invalidateQueries({ queryKey: ["tasks"] });
    },
  });
  const changePassword = useMutation({
    mutationFn: () => {
      setPasswordError("");
      if (password.newPassword !== password.confirmation) {
        throw new Error("Mật khẩu xác nhận không khớp.");
      }
      if (password.newPassword.length < 8) {
        throw new Error("Mật khẩu mới phải có ít nhất 8 ký tự.");
      }
      return api.post("/auth/change-password", {
        currentPassword: password.currentPassword,
        newPassword: password.newPassword,
      });
    },
    onSuccess: () => {
      setPassword({ currentPassword: "", newPassword: "", confirmation: "" });
    },
    onError: (error) =>
      setPasswordError(getApiErrorMessage(error, "Không thể đổi mật khẩu.")),
  });

  return (
    <>
      <div className="page-heading">
        <div>
          <span className="eyebrow">CÁ NHÂN HÓA</span>
          <h1>Cài đặt</h1>
          <p>Điều chỉnh Flowmate theo nhịp làm việc của riêng bạn.</p>
        </div>
      </div>
      <div className="settings-grid">
        <section className="panel settings-card">
          <h2>
            <UserRound size={20} /> Hồ sơ
          </h2>
          <div className="profile-summary">
            <span className="avatar large">
              {user?.fullName.charAt(0).toUpperCase()}
            </span>
            <div>
              <strong>{user?.fullName}</strong>
              <p>{user?.email}</p>
            </div>
          </div>
          <div className="form-grid settings-form">
            <label>
              Họ và tên
              <input
                value={profile.fullName}
                maxLength={120}
                onChange={(event) =>
                  setProfile((current) => ({
                    ...current,
                    fullName: event.target.value,
                  }))
                }
              />
            </label>
            <label>
              Múi giờ
              <select
                value={profile.timezone}
                onChange={(event) =>
                  setProfile((current) => ({
                    ...current,
                    timezone: event.target.value,
                  }))
                }
              >
                {timezones.map((timezone) => (
                  <option key={timezone} value={timezone}>
                    {timezone}
                  </option>
                ))}
              </select>
            </label>
          </div>
          <button
            className="primary"
            onClick={() => saveProfile.mutate()}
            disabled={!profile.fullName.trim() || saveProfile.isPending}
          >
            <Save size={17} />
            {saveProfile.isPending ? "Đang lưu..." : "Lưu hồ sơ"}
          </button>
          {saveProfile.isSuccess && (
            <span className="saved">Đã cập nhật hồ sơ</span>
          )}
          {saveProfile.isError && (
            <span className="form-error" role="alert">
              {getApiErrorMessage(
                saveProfile.error,
                "Không thể cập nhật hồ sơ.",
              )}
            </span>
          )}
        </section>

        <section className="panel settings-card">
          <h2>Giờ làm việc và hiển thị</h2>
          <div className="form-grid settings-form">
            <label>
              Bắt đầu
              <input
                type="time"
                value={form.workStartTime}
                onChange={(event) =>
                  setForm((current) => ({
                    ...current,
                    workStartTime: event.target.value,
                  }))
                }
              />
            </label>
            <label>
              Kết thúc
              <input
                type="time"
                value={form.workEndTime}
                onChange={(event) =>
                  setForm((current) => ({
                    ...current,
                    workEndTime: event.target.value,
                  }))
                }
              />
            </label>
            <label>
              Nhắc mặc định trước (phút)
              <input
                type="number"
                min={0}
                max={10080}
                value={form.defaultReminderMinutes}
                onChange={(event) =>
                  setForm((current) => ({
                    ...current,
                    defaultReminderMinutes: Number(event.target.value),
                  }))
                }
              />
            </label>
            <label>
              Ngày bắt đầu tuần
              <select
                value={form.weekStartDay}
                onChange={(event) =>
                  setForm((current) => ({
                    ...current,
                    weekStartDay: Number(event.target.value),
                  }))
                }
              >
                <option value={1}>Thứ Hai</option>
                <option value={7}>Chủ Nhật</option>
              </select>
            </label>
            <label>
              Giao diện
              <select
                value={form.theme}
                onChange={(event) =>
                  setForm((current) => ({
                    ...current,
                    theme: event.target.value as ThemePreference,
                  }))
                }
              >
                <option value="system">Theo hệ thống</option>
                <option value="light">Sáng</option>
                <option value="dark">Tối</option>
              </select>
            </label>
            <label>
              Ngôn ngữ
              <select
                value={form.language}
                onChange={(event) =>
                  setForm((current) => ({
                    ...current,
                    language: event.target.value,
                  }))
                }
              >
                <option value="vi">Tiếng Việt</option>
              </select>
            </label>
          </div>
          <button
            className="primary"
            onClick={() => saveSettings.mutate()}
            disabled={saveSettings.isPending}
          >
            <Save size={17} />
            {saveSettings.isPending ? "Đang lưu..." : "Lưu cài đặt"}
          </button>
          {saveSettings.isSuccess && (
            <span className="saved">Đã lưu thay đổi</span>
          )}
          {saveSettings.isError && (
            <span className="form-error" role="alert">
              {getApiErrorMessage(saveSettings.error, "Không thể lưu cài đặt.")}
            </span>
          )}
        </section>

        <section className="panel settings-card">
          <h2>
            <KeyRound size={20} /> Đổi mật khẩu
          </h2>
          <div className="form-grid settings-form password-form">
            <label>
              Mật khẩu hiện tại
              <input
                type="password"
                value={password.currentPassword}
                onChange={(event) =>
                  setPassword((current) => ({
                    ...current,
                    currentPassword: event.target.value,
                  }))
                }
              />
            </label>
            <label>
              Mật khẩu mới
              <input
                type="password"
                minLength={8}
                value={password.newPassword}
                onChange={(event) =>
                  setPassword((current) => ({
                    ...current,
                    newPassword: event.target.value,
                  }))
                }
              />
            </label>
            <label>
              Xác nhận mật khẩu mới
              <input
                type="password"
                minLength={8}
                value={password.confirmation}
                onChange={(event) =>
                  setPassword((current) => ({
                    ...current,
                    confirmation: event.target.value,
                  }))
                }
              />
            </label>
          </div>
          <button
            className="primary"
            onClick={() => changePassword.mutate()}
            disabled={
              changePassword.isPending ||
              !password.currentPassword ||
              !password.newPassword ||
              !password.confirmation
            }
          >
            <KeyRound size={17} />
            {changePassword.isPending ? "Đang đổi..." : "Đổi mật khẩu"}
          </button>
          {changePassword.isSuccess && (
            <span className="saved">Đã đổi mật khẩu thành công</span>
          )}
          {passwordError && (
            <span className="form-error" role="alert">
              {passwordError}
            </span>
          )}
        </section>

        <aside className="security-note">
          <ShieldCheck />
          <div>
            <strong>Dữ liệu thuộc về bạn</strong>
            <p>
              Mỗi yêu cầu đều được xác thực. Người dùng khác không thể truy cập
              công việc cá nhân của bạn.
            </p>
          </div>
        </aside>
      </div>
    </>
  );
}
