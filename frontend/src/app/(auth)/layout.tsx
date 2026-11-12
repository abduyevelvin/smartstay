import Link from "next/link";

export default function AuthLayout({ children }: { children: React.ReactNode }) {
    return (
        <div className="min-h-screen bg-muted/30">
            {/* Minimal top bar — logo links back home */}
            <div className="px-6 py-4">
                <Link href="/" className="text-xl font-bold tracking-tight">
                    Smart<span className="text-primary">Stay</span>
                </Link>
            </div>

            {/* Centered form */}
            <div className="flex items-center justify-center px-4 pb-16 pt-8">
                {children}
            </div>
        </div>
    );
}
