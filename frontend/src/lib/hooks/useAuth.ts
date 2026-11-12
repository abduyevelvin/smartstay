"use client";

import { useMutation } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { useAuthStore } from "@/lib/auth";
import { decodeJwt } from "@/lib/jwt";
import type { AuthResponse, LoginRequest, RegisterRequest } from "@/types";

// ── Cookie helpers (middleware reads this to protect routes) ───────────────
const SESSION_COOKIE = "smartstay_session";

function setSessionCookie() {
    document.cookie = `${SESSION_COOKIE}=1; path=/; SameSite=Lax; max-age=604800`; // 7 days
}

function clearSessionCookie() {
    document.cookie = `${SESSION_COOKIE}=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT`;
}

// ── API calls ──────────────────────────────────────────────────────────────
const loginApi = (data: LoginRequest) =>
    api.post<AuthResponse>("/api/v1/auth/login", data).then((r) => r.data);

const registerApi = (data: RegisterRequest) =>
    api.post<AuthResponse>("/api/v1/auth/register", data).then((r) => r.data);

const logoutApi = (refreshToken: string) =>
    api.post("/api/v1/auth/logout", {refreshToken});

// ── Shared: decode JWT and store user + tokens ─────────────────────────────
function storeAuth(data: AuthResponse) {
    const claims = decodeJwt(data.accessToken);
    const [firstName = "", ...rest] = claims.name.split(" ");
    const lastName = rest.join(" ");
    useAuthStore.getState().setAuth(
        {id: claims.userId, email: claims.sub, firstName, lastName, role: claims.role as never},
        data.accessToken,
        data.refreshToken
    );
    setSessionCookie();
}

// ── Hooks ──────────────────────────────────────────────────────────────────
export function useLogin() {
    return useMutation({
        mutationFn: loginApi,
        onSuccess: storeAuth,
    });
}

export function useRegister() {
    return useMutation({
        mutationFn: registerApi,
        onSuccess: storeAuth,
    });
}

export function useLogout() {
    const {refreshToken, clearAuth} = useAuthStore();
    return useMutation({
        mutationFn: () => logoutApi(refreshToken ?? ""),
        onSettled: () => {
            clearAuth();
            clearSessionCookie();
        },
    });
}
