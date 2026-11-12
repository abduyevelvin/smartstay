"use client";

import { usePathname } from "next/navigation";
import { Navbar } from "./Navbar";

// Pages that use their own layout (centered, no top nav)
const HIDDEN_ON = ["/login", "/register"];

export function ConditionalNavbar() {
    const pathname = usePathname();
    if (HIDDEN_ON.some((p) => pathname.startsWith(p))) return null;
    return <Navbar/>;
}
