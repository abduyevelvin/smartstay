"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api";
import type { AssignRoleRequest, Page, UserSummaryResponse } from "@/types";

const fetchAllUsers = (page = 0) =>
    api
        .get<Page<UserSummaryResponse>>("/api/v1/admin/users", {params: {page, size: 20, sort: "createdAt,desc"}})
        .then((r) => r.data);

const assignRoleApi = ({userId, role}: { userId: string; role: AssignRoleRequest["role"] }) =>
    api
        .patch<UserSummaryResponse>(`/api/v1/admin/users/${userId}/role`, {role})
        .then((r) => r.data);

// ── Query hooks ────────────────────────────────────────────────────────────
export function useAllUsers(page = 0) {
    return useQuery({
        queryKey: ["admin", "users", page],
        queryFn: () => fetchAllUsers(page),
    });
}

// ── Mutation hooks ─────────────────────────────────────────────────────────
export function useAssignRole() {
    const qc = useQueryClient();
    return useMutation({
        mutationFn: assignRoleApi,
        onSuccess: () => qc.invalidateQueries({queryKey: ["admin", "users"]}),
    });
}
