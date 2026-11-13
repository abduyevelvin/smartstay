"use client";

import { use } from "react";
import Link from "next/link";
import { ArrowLeft, Clock, MapPin, Sparkles } from "lucide-react";
import { StarRating } from "@/components/ui/StarRating";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { useHotel, useRooms } from "@/lib/hooks/useHotels";
import { useHotelReviews } from "@/lib/hooks/useReviews";
import { useHotelSummary } from "@/lib/hooks/useAI";
import { useAuthStore } from "@/lib/auth";
import { RoomCard } from "@/components/hotel/RoomCard";
import { ReviewCard } from "@/components/hotel/ReviewCard";

const WRAP = "mx-auto w-full max-w-screen-2xl px-4 sm:px-6 lg:px-10";

export default function HotelDetailPage({
                                            params,
                                        }: {
    params: Promise<{ id: string }>;
}) {
    const {id} = use(params);
    const {user} = useAuthStore();

    const {data: hotel, isLoading: hotelLoading} = useHotel(id);
    const {data: rooms, isLoading: roomsLoading} = useRooms(id);
    const {data: reviewsPage, isLoading: reviewsLoading} = useHotelReviews(id, 0);
    const {data: summary, isLoading: summaryLoading} = useHotelSummary(id);

    const canBook = user?.role === "GUEST";

    // ── Loading skeleton ───────────────────────────────────────────────────────
    if (hotelLoading) {
        return (
            <div className={`${WRAP} py-10 space-y-6`}>
                <div className="h-5 w-24 bg-muted animate-pulse rounded"/>
                <div className="h-80 bg-muted animate-pulse rounded-xl"/>
                <div className="h-32 bg-muted animate-pulse rounded-xl"/>
            </div>
        );
    }

    // ── Not found ──────────────────────────────────────────────────────────────
    if (!hotel) {
        return (
            <div className="flex min-h-[40vh] flex-col items-center justify-center gap-4">
                <p className="text-muted-foreground">Hotel not found.</p>
                <Link href="/"><Button variant="outline">Back to search</Button></Link>
            </div>
        );
    }

    return (
        <div className={`${WRAP} py-8 space-y-10`}>

            {/* ── Back link ─────────────────────────────────────────────────── */}
            <Link
                href="/"
                className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground transition-colors"
            >
                <ArrowLeft className="h-4 w-4"/> Back to hotels
            </Link>

            {/* ── Hero image ────────────────────────────────────────────────── */}
            <div className="h-72 sm:h-96 w-full overflow-hidden rounded-xl bg-muted">
                {hotel.images?.[0] ? (
                    <img
                        src={hotel.images[0]}
                        alt={hotel.name}
                        className="h-full w-full object-cover"
                    />
                ) : (
                    <div className="flex h-full items-center justify-center text-muted-foreground">
                        No image available
                    </div>
                )}
            </div>

            {/* ── Header info ───────────────────────────────────────────────── */}
            <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
                <div className="space-y-2">
                    <div className="flex flex-wrap items-center gap-2">
                        <h1 className="text-2xl font-bold sm:text-3xl">{hotel.name}</h1>
                        <StarRating rating={hotel.starRating} className="h-4 w-4"/>
                    </div>
                    <div className="flex items-center gap-1 text-sm text-muted-foreground">
                        <MapPin className="h-4 w-4 shrink-0"/>
                        <span>{hotel.address}, {hotel.city}, {hotel.country}</span>
                    </div>
                    {hotel.averageRating > 0 && (
                        <div className="flex items-center gap-2">
                            <Badge variant="secondary">★ {hotel.averageRating.toFixed(1)}</Badge>
                            <span className="text-sm text-muted-foreground">
                                {hotel.reviewCount} review{hotel.reviewCount !== 1 ? "s" : ""}
                            </span>
                        </div>
                    )}
                </div>

                <div className="flex shrink-0 flex-col gap-1 text-sm text-muted-foreground sm:items-end">
                    <div className="flex items-center gap-1.5">
                        <Clock className="h-4 w-4"/>
                        <span>Check-in: <strong className="text-foreground">{hotel.checkInTime}</strong></span>
                    </div>
                    <div className="flex items-center gap-1.5">
                        <Clock className="h-4 w-4"/>
                        <span>Check-out: <strong className="text-foreground">{hotel.checkOutTime}</strong></span>
                    </div>
                </div>
            </div>

            {/* ── Description ───────────────────────────────────────────────── */}
            {hotel.description && (
                <div>
                    <h2 className="mb-2 text-lg font-semibold">About</h2>
                    <p className="leading-relaxed text-muted-foreground">{hotel.description}</p>
                </div>
            )}

            {/* ── Amenities ─────────────────────────────────────────────────── */}
            {hotel.amenities?.length > 0 && (
                <div>
                    <h2 className="mb-3 text-lg font-semibold">Amenities</h2>
                    <div className="flex flex-wrap gap-2">
                        {hotel.amenities.map((a) => (
                            <Badge key={a} variant="outline">{a}</Badge>
                        ))}
                    </div>
                </div>
            )}

            {/* ── AI Summary ────────────────────────────────────────────────── */}
            <Card>
                <CardHeader className="pb-2">
                    <CardTitle className="flex items-center gap-2 text-base">
                        <Sparkles className="h-4 w-4 text-primary"/> AI Summary
                    </CardTitle>
                </CardHeader>
                <CardContent>
                    {summaryLoading ? (
                        <div className="space-y-2">
                            <div className="h-4 w-full animate-pulse rounded bg-muted"/>
                            <div className="h-4 w-4/5 animate-pulse rounded bg-muted"/>
                            <div className="h-4 w-3/5 animate-pulse rounded bg-muted"/>
                        </div>
                    ) : summary?.summary ? (
                        <p className="text-sm leading-relaxed text-muted-foreground">
                            {summary.summary}
                        </p>
                    ) : (
                        <p className="text-sm text-muted-foreground">Summary not available.</p>
                    )}
                </CardContent>
            </Card>

            {/* ── Rooms ─────────────────────────────────────────────────────── */}
            <div>
                <h2 className="mb-4 text-lg font-semibold">Available Rooms</h2>
                {roomsLoading ? (
                    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                        {[1, 2].map((i) => (
                            <div key={i} className="h-44 animate-pulse rounded-xl bg-muted"/>
                        ))}
                    </div>
                ) : !rooms?.length ? (
                    <p className="text-muted-foreground">No rooms listed for this hotel.</p>
                ) : (
                    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                        {rooms.map((room) => (
                            <RoomCard key={room.id} room={room} hotelId={id} canBook={canBook}/>
                        ))}
                    </div>
                )}
            </div>

            {/* ── Reviews ───────────────────────────────────────────────────── */}
            <div>
                <h2 className="mb-4 text-lg font-semibold">
                    Guest Reviews
                    {(reviewsPage?.totalElements ?? 0) > 0 && (
                        <span className="ml-2 text-sm font-normal text-muted-foreground">
                            ({reviewsPage!.totalElements})
                        </span>
                    )}
                </h2>
                {reviewsLoading ? (
                    <div className="space-y-3">
                        {[1, 2].map((i) => (
                            <div key={i} className="h-32 animate-pulse rounded-xl bg-muted"/>
                        ))}
                    </div>
                ) : !reviewsPage?.content?.length ? (
                    <p className="text-muted-foreground">
                        No reviews yet — be the first to review after your stay!
                    </p>
                ) : (
                    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                        {reviewsPage.content.map((review) => (
                            <ReviewCard key={review.id} review={review}/>
                        ))}
                    </div>
                )}
            </div>

        </div>
    );
}
