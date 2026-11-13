"use client";

import { useEffect } from "react";
import axios from "axios";
import { clearSessionCookie, setSessionCookie, useAuthStore } from "@/lib/auth";
import { decodeJwt } from "@/lib/jwt";

const BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

/**
 * Invisible component — renders null, side-effects only.
 *
 * On every page load, Zustand rehydrates `refreshToken` from localStorage
 * (via the `persist` middleware) but `accessToken` is always null until a
 * fresh token is issued. This component detects that state and silently
 * calls the refresh endpoint so the user never sees a broken session after
 * a page reload.
 *
 * Failure path: if the refresh token is expired or invalid, `clearAuth()`
 * clears both the store and the session cookie, so middleware redirects the
 * user to /login instead of trapping them in a failing API loop.
 */
export function AuthHydrator() {
    useEffect(() => {
        const {refreshToken, accessToken, setAuth, clearAuth} = useAuthStore.getState();

        // Already hydrated (normal navigation) or no previous session → nothing to do
        if (!refreshToken || accessToken) return;

        // We have a refresh token but no access token (page reload).
        // Clear the stale session cookie NOW so the middleware won't redirect the
        // user away from /login or /register while the refresh is still in flight.
        // setSessionCookie() restores it on success; clearAuth() keeps it gone on failure.
        clearSessionCookie();

        axios
            .post<{ accessToken: string }>(`${BASE_URL}/api/v1/auth/refresh`, {refreshToken})
            .then(({data}) => {
                const claims = decodeJwt(data.accessToken);
                const [firstName = "", ...rest] = claims.name.split(" ");
                setAuth(
                    {
                        id: claims.userId,
                        email: claims.sub,
                        firstName,
                        lastName: rest.join(" "),
                        role: claims.role as never,
                    },
                    data.accessToken,
                    refreshToken,   // keep the same refresh token
                );
                setSessionCookie();
            })
            .catch(() => {
                // Refresh token expired or revoked — full logout
                clearAuth();
            });
    }, []); // eslint-disable-line react-hooks/exhaustive-deps

    return null;
}
