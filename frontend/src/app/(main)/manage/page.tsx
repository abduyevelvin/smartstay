"use client";

import { useState } from "react";
import { Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { ManagerHotelCard } from "@/components/manage/ManagerHotelCard";
import { AddHotelModal } from "@/components/manage/AddHotelModal";
import { useManagerHotels } from "@/lib/hooks/useHotels";
import { useAuthStore } from "@/lib/auth";

export default function ManageDashboardPage() {
    const [addHotelOpen, setAddHotelOpen] = useState(false);
    const {user} = useAuthStore();
    const {data: hotels, isLoading} = useManagerHotels(user?.id ?? "");

    return (
        <div className="mx-auto w-full max-w-screen-2xl px-4 sm:px-6 lg:px-10 py-8">
            {/* Page header */}
            <div className="mb-8 flex items-center justify-between gap-4">
                <div>
                    <h1 className="text-2xl font-bold">Hotel Manager Dashboard</h1>
                    <p className="mt-1 text-sm text-muted-foreground">
                        Manage your properties, rooms, and bookings
                    </p>
                </div>
                <Button
                    onClick={() => setAddHotelOpen(true)}
                    className="flex items-center gap-2 shrink-0"
                >
                    <Plus className="h-4 w-4"/>
                    Add Hotel
                </Button>
            </div>

            {/* Loading skeleton */}
            {isLoading && (
                <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
                    {[1, 2, 3].map((i) => (
                        <div key={i} className="h-56 animate-pulse rounded-xl bg-muted"/>
                    ))}
                </div>
            )}

            {/* Empty state */}
            {!isLoading && !hotels?.length && (
                <div
                    className="flex flex-col items-center justify-center rounded-xl border-2 border-dashed py-20 text-center">
                    <p className="text-lg font-medium text-muted-foreground">
                        No hotels yet
                    </p>
                    <p className="mt-1 text-sm text-muted-foreground">
                        Add your first property to start managing bookings
                    </p>
                    <Button
                        variant="outline"
                        className="mt-4 flex items-center gap-2"
                        onClick={() => setAddHotelOpen(true)}
                    >
                        <Plus className="h-4 w-4"/>
                        Add your first hotel
                    </Button>
                </div>
            )}

            {/* Hotel grid */}
            {!isLoading && !!hotels?.length && (
                <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
                    {hotels.map((hotel) => (
                        <ManagerHotelCard key={hotel.id} hotel={hotel}/>
                    ))}
                </div>
            )}

            {/* Add Hotel Modal */}
            <AddHotelModal open={addHotelOpen} onOpenChange={setAddHotelOpen}/>
        </div>
    );
}
