import { create } from "zustand";
import { persist } from "zustand/middleware";

export type Role = "GUEST" | "HOTEL_MANAGER" | "ADMIN";

export interface AuthUser {
    id: string;
    email: string;
    firstName: string;
    lastName: string;
    role: Role;
}

interface AuthState {
    user: AuthUser | null;
    accessToken: string | null;
    refreshToken: string | null;
    isAuthenticated: boolean;

    // actions
    setAuth: (user: AuthUser, accessToken: string, refreshToken: string) => void;
    setAccessToken: (token: string) => void;
    clearAuth: () => void;
}

// ── Session cookie helpers ────────────────────────────────────────────────────
// The middleware (server-side) reads smartstay_session to gate protected routes.
// These helpers live here so clearAuth() can always clear the cookie — even when
// called from the axios interceptor in api.ts, which has no reference to useAuth.ts.

const SESSION_COOKIE = "smartstay_session";

export function setSessionCookie() {
    if (typeof document === "undefined") return;
    document.cookie = `${SESSION_COOKIE}=1; path=/; SameSite=Lax; max-age=604800`; // 7 days
}

function clearSessionCookie() {
    if (typeof document === "undefined") return;
    document.cookie = `${SESSION_COOKIE}=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT`;
}

// ── Store ─────────────────────────────────────────────────────────────────────
// Only refreshToken is persisted to localStorage. accessToken is intentionally
// kept in memory only — short-lived, no need to persist. On page reload,
// AuthHydrator detects the persisted refreshToken and silently re-issues a new
// accessToken before any API calls are made.

export const useAuthStore = create<AuthState>()(
    persist(
        (set) => ({
            user: null,
            accessToken: null,
            refreshToken: null,
            isAuthenticated: false,

            setAuth: (user, accessToken, refreshToken) =>
                set({user, accessToken, refreshToken, isAuthenticated: true}),

            setAccessToken: (accessToken) => set({accessToken}),

            // Clears Zustand state AND the session cookie so middleware
            // stops treating the browser as logged-in.
            clearAuth: () => {
                clearSessionCookie();
                set({user: null, accessToken: null, refreshToken: null, isAuthenticated: false});
            },
        }),
        {
            name: "smartstay-auth",
            // Persist only the refresh token — not the short-lived access token
            // or user object (both are reconstructed on mount via AuthHydrator).
            partialize: (s) => ({refreshToken: s.refreshToken}),
        }
    )
);
