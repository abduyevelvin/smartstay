"use client";

import { useState } from "react";
import { Shield, ShieldCheck, ShieldOff, User } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useAllUsers, useAssignRole } from "@/lib/hooks/useAdmin";
import { getErrorMessage } from "@/lib/error";
import type { Role, UserSummaryResponse } from "@/types";

const ROLE_BADGE: Record<Role, { label: string; className: string }> = {
    GUEST: {label: "Guest", className: "bg-gray-100 text-gray-600"},
    HOTEL_MANAGER: {label: "Hotel Manager", className: "bg-blue-100 text-blue-700"},
    ADMIN: {label: "Admin", className: "bg-purple-100 text-purple-700"},
};

function UserRow({user}: { user: UserSummaryResponse }) {
    const [confirm, setConfirm] = useState<"promote" | "demote" | null>(null);
    const {mutate: assignRole, isPending, error} = useAssignRole();

    const badge = ROLE_BADGE[user.role];
    const isAdmin = user.role === "ADMIN";

    const handleAssign = (role: "GUEST" | "HOTEL_MANAGER") => {
        assignRole({userId: user.id, role}, {onSuccess: () => setConfirm(null)});
    };

    return (
        <tr className="border-b last:border-0 hover:bg-muted/40 transition-colors">
            {/* Name + email */}
            <td className="px-4 py-3">
                <div className="flex items-center gap-2.5">
                    <div className="flex h-8 w-8 items-center justify-center rounded-full bg-muted shrink-0">
                        <User className="h-4 w-4 text-muted-foreground"/>
                    </div>
                    <div className="min-w-0">
                        <p className="font-medium text-sm truncate">
                            {user.firstName} {user.lastName}
                        </p>
                        <p className="text-xs text-muted-foreground truncate">{user.email}</p>
                    </div>
                </div>
            </td>

            {/* Role badge */}
            <td className="px-4 py-3">
                <span className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${badge.className}`}>
                    {badge.label}
                </span>
            </td>

            {/* Status */}
            <td className="px-4 py-3">
                <span className={`text-xs font-medium ${user.active ? "text-green-600" : "text-red-500"}`}>
                    {user.active ? "Active" : "Inactive"}
                </span>
            </td>

            {/* Joined */}
            <td className="px-4 py-3 text-xs text-muted-foreground">
                {new Date(user.createdAt).toLocaleDateString()}
            </td>

            {/* Actions */}
            <td className="px-4 py-3 text-right">
                {isAdmin ? (
                    <span className="text-xs text-muted-foreground italic">Protected</span>
                ) : confirm === null ? (
                    <div className="flex items-center justify-end gap-1.5">
                        {user.role === "GUEST" && (
                            <Button
                                size="sm"
                                variant="outline"
                                className="h-7 text-xs gap-1"
                                onClick={() => setConfirm("promote")}
                            >
                                <ShieldCheck className="h-3 w-3"/>
                                Make Manager
                            </Button>
                        )}
                        {user.role === "HOTEL_MANAGER" && (
                            <Button
                                size="sm"
                                variant="ghost"
                                className="h-7 text-xs text-destructive hover:text-destructive hover:bg-destructive/10 gap-1"
                                onClick={() => setConfirm("demote")}
                            >
                                <ShieldOff className="h-3 w-3"/>
                                Remove Manager
                            </Button>
                        )}
                    </div>
                ) : (
                    <div className="flex items-center justify-end gap-2">
                        <span className="text-xs text-muted-foreground">
                            {confirm === "promote" ? "Promote to Hotel Manager?" : "Demote to Guest?"}
                        </span>
                        <Button
                            size="sm"
                            variant={confirm === "promote" ? "default" : "destructive"}
                            className="h-7 text-xs"
                            disabled={isPending}
                            onClick={() => handleAssign(confirm === "promote" ? "HOTEL_MANAGER" : "GUEST")}
                        >
                            {isPending ? "Saving…" : "Confirm"}
                        </Button>
                        <Button
                            size="sm"
                            variant="ghost"
                            className="h-7 text-xs"
                            onClick={() => setConfirm(null)}
                        >
                            Cancel
                        </Button>
                    </div>
                )}
                {error && (
                    <p className="mt-1 text-xs text-destructive text-right">{getErrorMessage(error)}</p>
                )}
            </td>
        </tr>
    );
}

export default function AdminUsersPage() {
    const [page, setPage] = useState(0);
    const {data, isLoading} = useAllUsers(page);

    return (
        <div className="mx-auto w-full max-w-screen-xl px-4 sm:px-6 lg:px-10 py-8">
            {/* Header */}
            <div className="mb-8 flex items-center gap-3">
                <Shield className="h-6 w-6 text-primary"/>
                <div>
                    <h1 className="text-2xl font-bold">User Management</h1>
                    <p className="mt-0.5 text-sm text-muted-foreground">
                        Promote guests to Hotel Manager, or revoke that role
                    </p>
                </div>
            </div>

            {/* Stats */}
            {data && (
                <div className="mb-6 grid grid-cols-3 gap-4">
                    {(["GUEST", "HOTEL_MANAGER", "ADMIN"] as Role[]).map((role) => {
                        const count = data.content.filter((u) => u.role === role).length;
                        const b = ROLE_BADGE[role];
                        return (
                            <div key={role} className="rounded-lg border bg-card p-4 text-center">
                                <p className={`text-xs font-medium uppercase tracking-wide ${b.className} inline-block rounded-full px-2 py-0.5 mb-1`}>
                                    {b.label}
                                </p>
                                <p className="text-2xl font-bold">{count}</p>
                                <p className="text-xs text-muted-foreground">on this page</p>
                            </div>
                        );
                    })}
                </div>
            )}

            {/* Table */}
            <div className="rounded-xl border overflow-hidden">
                <table className="w-full text-sm">
                    <thead className="bg-muted/50 text-muted-foreground">
                    <tr>
                        <th className="px-4 py-3 text-left font-medium">User</th>
                        <th className="px-4 py-3 text-left font-medium">Role</th>
                        <th className="px-4 py-3 text-left font-medium">Status</th>
                        <th className="px-4 py-3 text-left font-medium">Joined</th>
                        <th className="px-4 py-3 text-right font-medium">Actions</th>
                    </tr>
                    </thead>
                    <tbody>
                    {isLoading &&
                        [1, 2, 3, 4, 5].map((i) => (
                            <tr key={i} className="border-b">
                                <td colSpan={5} className="px-4 py-3">
                                    <div className="h-8 animate-pulse rounded bg-muted"/>
                                </td>
                            </tr>
                        ))}
                    {!isLoading && data?.content.map((user) => (
                        <UserRow key={user.id} user={user}/>
                    ))}
                    {!isLoading && !data?.content.length && (
                        <tr>
                            <td colSpan={5} className="px-4 py-10 text-center text-sm text-muted-foreground">
                                No users found
                            </td>
                        </tr>
                    )}
                    </tbody>
                </table>
            </div>

            {/* Pagination */}
            {data && data.totalPages > 1 && (
                <div className="mt-6 flex items-center justify-center gap-4">
                    <Button
                        variant="outline"
                        size="sm"
                        disabled={page === 0}
                        onClick={() => setPage((p) => p - 1)}
                    >
                        Previous
                    </Button>
                    <span className="text-sm text-muted-foreground">
                        Page {page + 1} of {data.totalPages}
                    </span>
                    <Button
                        variant="outline"
                        size="sm"
                        disabled={page >= data.totalPages - 1}
                        onClick={() => setPage((p) => p + 1)}
                    >
                        Next
                    </Button>
                </div>
            )}
        </div>
    );
}
