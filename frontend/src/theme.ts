export type ThemePreference = "light" | "dark" | "system";

export function applyTheme(theme: ThemePreference) {
  const resolved =
    theme === "system"
      ? window.matchMedia("(prefers-color-scheme: dark)").matches
        ? "dark"
        : "light"
      : theme;
  document.documentElement.dataset.theme = resolved;
  localStorage.setItem("themePreference", theme);
}

export function applyStoredTheme() {
  const stored = localStorage.getItem(
    "themePreference",
  ) as ThemePreference | null;
  applyTheme(stored || "system");
}
