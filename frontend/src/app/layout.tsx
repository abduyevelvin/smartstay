import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import { Providers } from "@/components/providers";
import { ConditionalNavbar } from "@/components/layout/ConditionalNavbar";
import { ChatWidget } from "@/components/chat/ChatWidget";
import "./globals.css";

const geistSans = Geist({
    variable: "--font-geist-sans",
    subsets: ["latin"],
});

const geistMono = Geist_Mono({
    variable: "--font-geist-mono",
    subsets: ["latin"],
});

export const metadata: Metadata = {
    title: "SmartStay — AI-Powered Hotel Booking",
    description: "Find and book hotels with AI-powered recommendations",
};

export default function RootLayout({
                                       children,
                                   }: Readonly<{
    children: React.ReactNode;
}>) {
    return (
        <html lang="en">
        <body
            className={`${geistSans.variable} ${geistMono.variable} antialiased`}
            suppressHydrationWarning
        >
        <Providers>
            <ConditionalNavbar/>
            {children}
            <ChatWidget/>
        </Providers>
        </body>
        </html>
    );
}
