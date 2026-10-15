import { createContext, useContext, useState, type ReactNode } from "react";
import type { AuthResponse, User } from "./types";
type AuthContextType = {
  user: User | null;
  login: (data: AuthResponse) => void;
  updateUser: (user: User) => void;
  logout: () => void;
};
const AuthContext = createContext<AuthContextType | null>(null);
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(() => {
    try {
      return JSON.parse(localStorage.getItem("user") || "null");
    } catch {
      return null;
    }
  });
  const login = (d: AuthResponse) => {
    localStorage.setItem("accessToken", d.accessToken);
    localStorage.setItem("refreshToken", d.refreshToken);
    localStorage.setItem("user", JSON.stringify(d.user));
    setUser(d.user);
  };
  const updateUser = (value: User) => {
    localStorage.setItem("user", JSON.stringify(value));
    setUser(value);
  };
  const logout = () => {
    localStorage.removeItem("accessToken");
    localStorage.removeItem("refreshToken");
    localStorage.removeItem("user");
    setUser(null);
  };
  return (
    <AuthContext.Provider value={{ user, login, updateUser, logout }}>
      {children}
    </AuthContext.Provider>
  );
}
export function useAuth() {
  const c = useContext(AuthContext);
  if (!c) throw new Error("AuthProvider missing");
  return c;
}
