import { Suspense } from "react";
import { HomePageContent } from "./HomePageContent";

// HomePageContent uses useSearchParams() — must be wrapped in Suspense (Next.js 15)
export default function HomePage() {
    return (
        <Suspense>
            <HomePageContent />
        </Suspense>
    );
}
