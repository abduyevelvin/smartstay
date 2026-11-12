"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { useAuthStore } from "@/lib/auth";
import { useLogout } from "@/lib/hooks/useAuth";

export function Navbar() {
    const router = useRouter();
    const {isAuthenticated, user} = useAuthStore();
    const {mutate: logout, isPending} = useLogout();

    const handleLogout = () => {
        logout(undefined, {onSuccess: () => router.push("/")});
    };

    return (
        <header className="sticky top-0 z-50 border-b bg-background/95 backdrop-blur">
            <div className="flex h-16 w-full items-center justify-between px-4 sm:px-6 lg:px-10">
                {/* Logo */}
                <Link href="/" className="text-xl font-bold tracking-tight">
                    Smart<span className="text-primary">Stay</span>
                </Link>

                {/* Right side */}
                <nav className="flex items-center gap-2">
                    {isAuthenticated ? (
                        <>
                            <span className="hidden sm:block text-sm text-muted-foreground mr-2">
                                Hi, {user?.firstName}
                            </span>
                            <Link href="/bookings">
                                <Button variant="ghost" size="sm">My Bookings</Button>
                            </Link>
                            {user?.role === "HOTEL_MANAGER" && (
                                <Link href="/manage">
                                    <Button variant="ghost" size="sm">Dashboard</Button>
                                </Link>
                            )}
                            {user?.role === "ADMIN" && (
                                <Link href="/admin/users">
                                    <Button variant="ghost" size="sm">Admin</Button>
                                </Link>
                            )}
                            <Button
                                variant="outline"
                                size="sm"
                                disabled={isPending}
                                onClick={handleLogout}
                            >
                                Sign out
                            </Button>
                        </>
                    ) : (
                        <>
                            <Link href="/login">
                                <Button variant="ghost" size="sm">Sign in</Button>
                            </Link>
                            <Link href="/register">
                                <Button size="sm">Register</Button>
                            </Link>
                        </>
                    )}
                </nav>
            </div>
        </header>
    );
}
