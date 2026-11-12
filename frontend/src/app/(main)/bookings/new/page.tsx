import { Suspense } from "react";
import { BookingForm } from "./BookingForm";

// BookingForm uses useSearchParams() — must be wrapped in Suspense (Next.js 15)
export default function BookingNewPage() {
    return (
        <Suspense
            fallback={
                <div className="mx-auto w-full max-w-screen-2xl px-4 sm:px-6 lg:px-10 py-10 space-y-6">
                    <div className="h-5 w-24 animate-pulse rounded bg-muted"/>
                    <div className="h-80 animate-pulse rounded-xl bg-muted"/>
                </div>
            }
        >
            <BookingForm/>
        </Suspense>
    );
}
