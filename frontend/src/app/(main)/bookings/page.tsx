"use client";

import { useState } from "react";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { BookingCard } from "@/components/booking/BookingCard";
import { useMyBookings } from "@/lib/hooks/useBookings";

export default function BookingsPage() {
    const [page, setPage] = useState(0);
    const {data, isLoading} = useMyBookings(page);

    return (
        <div className="mx-auto w-full max-w-screen-2xl px-4 sm:px-6 lg:px-10 py-8">
            <h1 className="mb-8 text-2xl font-bold">My Bookings</h1>

            {/* Loading */}
            {isLoading && (
                <div className="space-y-4">
                    {[1, 2, 3].map((i) => (
                        <div key={i} className="h-44 animate-pulse rounded-xl bg-muted"/>
                    ))}
                </div>
            )}

            {/* Empty */}
            {!isLoading && !data?.content?.length && (
                <div className="flex flex-col items-center justify-center py-20 text-center">
                    <p className="text-lg font-medium text-muted-foreground">No bookings yet</p>
                    <p className="mt-1 text-sm text-muted-foreground">
                        Find a hotel and book your first stay!
                    </p>
                    <Link href="/">
                        <Button variant="outline" className="mt-4">Browse hotels</Button>
                    </Link>
                </div>
            )}

            {/* Booking grid */}
            {!isLoading && !!data?.content?.length && (
                <>
                    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
                        {data.content.map((booking) => (
                            <BookingCard key={booking.id} booking={booking}/>
                        ))}
                    </div>

                    {/* Pagination */}
                    {data.totalPages > 1 && (
                        <div className="mt-8 flex items-center justify-center gap-4">
                            <Button
                                variant="outline"
                                size="sm"
                                disabled={page === 0}
                                onClick={() => setPage((p) => p - 1)}
                            >
                                Previous
                            </Button>
                            <span className="text-sm text-muted-foreground">
                                Page {page + 1} of {data.totalPages}
                            </span>
                            <Button
                                variant="outline"
                                size="sm"
                                disabled={page >= data.totalPages - 1}
                                onClick={() => setPage((p) => p + 1)}
                            >
                                Next
                            </Button>
                        </div>
                    )}
                </>
            )}
        </div>
    );
}
