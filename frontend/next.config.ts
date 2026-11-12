import type { NextConfig } from "next";

const nextConfig: NextConfig = {
    output: "standalone",  // Produces .next/standalone — minimal self-contained server for Docker
};

export default nextConfig;
