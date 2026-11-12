import { NextRequest, NextResponse } from "next/server";

// Routes that require a logged-in user
const PROTECTED = ["/bookings", "/manage", "/admin"];

// Routes only for unauthenticated users
const AUTH_ONLY = ["/login", "/register"];

export function middleware(request: NextRequest) {
    const {pathname} = request.nextUrl;
    const hasSession = request.cookies.has("smartstay_session");

    const isProtected = PROTECTED.some((p) => pathname.startsWith(p));
    const isAuthOnly = AUTH_ONLY.some((p) => pathname.startsWith(p));

    // Not logged in → redirect to /login with a "from" param so we can bounce back
    if (isProtected && !hasSession) {
        const url = new URL("/login", request.url);
        url.searchParams.set("from", pathname);
        return NextResponse.redirect(url);
    }

    // Already logged in → no need to see login/register pages
    if (isAuthOnly && hasSession) {
        return NextResponse.redirect(new URL("/", request.url));
    }

    return NextResponse.next();
}

export const config = {
    matcher: ["/bookings/:path*", "/manage/:path*", "/admin/:path*", "/login", "/register"],
};
