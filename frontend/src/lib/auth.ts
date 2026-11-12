import { create } from "zustand";

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

export const useAuthStore = create<AuthState>((set) => ({
    user: null,
    accessToken: null,
    refreshToken: null,
    isAuthenticated: false,

    setAuth: (user, accessToken, refreshToken) =>
        set({user, accessToken, refreshToken, isAuthenticated: true}),

    setAccessToken: (accessToken) => set({accessToken}),

    clearAuth: () =>
        set({user: null, accessToken: null, refreshToken: null, isAuthenticated: false}),
}));
