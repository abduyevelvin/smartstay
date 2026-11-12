import axios, { AxiosError, InternalAxiosRequestConfig } from "axios";
import { useAuthStore } from "@/lib/auth";

const BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export const api = axios.create({
    baseURL: BASE_URL,
    headers: {"Content-Type": "application/json"},
});

// ── Request interceptor: attach access token ──────────────────────────────
api.interceptors.request.use((config: InternalAxiosRequestConfig) => {
    const token = useAuthStore.getState().accessToken;
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

// ── Response interceptor: silent refresh on 401 ───────────────────────────
let isRefreshing = false;
let failedQueue: Array<{
    resolve: (value: string) => void;
    reject: (reason: unknown) => void;
}> = [];

function processQueue(error: unknown, token: string | null) {
    failedQueue.forEach((p) => (error ? p.reject(error) : p.resolve(token!)));
    failedQueue = [];
}

api.interceptors.response.use(
    (response) => response,
    async (error: AxiosError) => {
        const original = error.config as InternalAxiosRequestConfig & {
            _retry?: boolean;
        };

        if (error.response?.status !== 401 || original._retry) {
            return Promise.reject(error);
        }

        if (isRefreshing) {
            return new Promise<string>((resolve, reject) => {
                failedQueue.push({resolve, reject});
            }).then((token) => {
                original.headers.Authorization = `Bearer ${token}`;
                return api(original);
            });
        }

        original._retry = true;
        isRefreshing = true;

        const refreshToken = useAuthStore.getState().refreshToken;

        if (!refreshToken) {
            useAuthStore.getState().clearAuth();
            return Promise.reject(error);
        }

        try {
            const {data} = await axios.post<{ accessToken: string }>(
                `${BASE_URL}/api/v1/auth/refresh`,
                {refreshToken}
            );
            const newToken = data.accessToken;
            useAuthStore.getState().setAccessToken(newToken);
            processQueue(null, newToken);
            original.headers.Authorization = `Bearer ${newToken}`;
            return api(original);
        } catch (refreshError) {
            processQueue(refreshError, null);
            useAuthStore.getState().clearAuth();
            return Promise.reject(refreshError);
        } finally {
            isRefreshing = false;
        }
    }
);
