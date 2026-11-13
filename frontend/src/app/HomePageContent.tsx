"use client";

import { useSearchParams } from "next/navigation";
import { SearchBar } from "@/components/hotel/SearchBar";
import { HotelGrid } from "@/components/hotel/HotelGrid";
import { useHotels } from "@/lib/hooks/useHotels";

export function HomePageContent() {
    const searchParams = useSearchParams();
    const city = searchParams.get("city") ?? undefined;

    const {data, isLoading} = useHotels({city, size: 12});

    return (
        <div className="min-h-screen">
            {/* Hero */}
            <section className="bg-gradient-to-br from-primary/10 via-background to-background py-20">
                <div className="mx-auto w-full max-w-screen-2xl px-4 sm:px-6 lg:px-10 text-center space-y-6">
                    <h1 className="text-4xl sm:text-5xl font-bold tracking-tight">
                        Find your perfect stay
                    </h1>
                    <p className="text-muted-foreground text-lg max-w-md mx-auto">
                        AI-powered hotel recommendations tailored to your preferences
                    </p>
                    <div className="flex justify-center pt-2">
                        <SearchBar defaultCity={city}/>
                    </div>
                </div>
            </section>

            {/* Results */}
            <section className="mx-auto w-full max-w-screen-2xl px-4 sm:px-6 lg:px-10 py-10">
                <h2 className="text-xl font-semibold mb-6">
                    {city ? `Hotels in ${city}` : "All Hotels"}
                </h2>
                <HotelGrid hotels={data?.content ?? []} isLoading={isLoading}/>

                {/* Pagination info */}
                {data && data.totalPages > 1 && (
                    <p className="text-center text-sm text-muted-foreground mt-8">
                        Showing page {data.number + 1} of {data.totalPages}
                        {" "}({data.totalElements} hotels total)
                    </p>
                )}
            </section>
        </div>
    );
}
