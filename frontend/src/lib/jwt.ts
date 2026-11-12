interface JwtPayload {
    sub: string;      // email
    userId: string;
    role: string;
    name: string;     // "firstName lastName"
    exp: number;
    iat: number;
}

/** Decode JWT payload without verification (server already verified it). */
export function decodeJwt(token: string): JwtPayload {
    const base64 = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    return JSON.parse(atob(base64));
}
