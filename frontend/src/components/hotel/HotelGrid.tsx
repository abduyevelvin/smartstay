import { HotelCard } from "./HotelCard";
import type { HotelResponse } from "@/types";

interface HotelGridProps {
    hotels: HotelResponse[];
    isLoading?: boolean;
}

// Skeleton card shown while loading
function SkeletonCard() {
    return (
        <div className="rounded-xl border bg-card overflow-hidden animate-pulse">
            <div className="h-48 bg-muted"/>
            <div className="p-4 space-y-3">
                <div className="h-4 bg-muted rounded w-3/4"/>
                <div className="h-3 bg-muted rounded w-1/2"/>
                <div className="h-3 bg-muted rounded w-1/3"/>
            </div>
        </div>
    );
}

export function HotelGrid({hotels, isLoading}: HotelGridProps) {
    if (isLoading) {
        return (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
                {Array.from({length: 6}).map((_, i) => <SkeletonCard key={i}/>)}
            </div>
        );
    }

    if (hotels.length === 0) {
        return (
            <div className="flex flex-col items-center justify-center py-20 text-center text-muted-foreground">
                <p className="text-lg font-medium">No hotels found</p>
                <p className="text-sm mt-1">Try a different city or clear the search</p>
            </div>
        );
    }

    return (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
            {hotels.map((hotel) => (
                <HotelCard key={hotel.id} hotel={hotel}/>
            ))}
        </div>
    );
}
