"use client";

import { useState } from "react";
import { Calendar, MapPin } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import {
    AlertDialog,
    AlertDialogAction,
    AlertDialogCancel,
    AlertDialogContent,
    AlertDialogDescription,
    AlertDialogFooter,
    AlertDialogHeader,
    AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { useHotel } from "@/lib/hooks/useHotels";
import { useCancelBooking } from "@/lib/hooks/useBookings";
import { ReviewModal } from "./ReviewModal";
import type { BookingResponse } from "@/types";

// ── Status badge styles ───────────────────────────────────────────────────────
const STATUS_CLASS: Record<BookingResponse["status"], string> = {
    CONFIRMED: "bg-green-100 text-green-700",
    CANCELLED: "bg-gray-100 text-gray-500",
};

interface BookingCardProps {
    booking: BookingResponse;
}

export function BookingCard({booking}: BookingCardProps) {
    const [reviewOpen, setReviewOpen] = useState(false);
    const [cancelOpen, setCancelOpen] = useState(false);

    const {data: hotel} = useHotel(booking.hotelId);
    const {mutate: cancel, isPending: cancelling} = useCancelBooking();

    const canCancel = booking.status === "CONFIRMED";
    // A guest can review once their stay has ended (checkout date is today or in the past)
    const canReview = booking.status === "CONFIRMED" && new Date(booking.checkOut) <= new Date();

    const nights = Math.max(
        1,
        Math.floor(
            (new Date(booking.checkOut).getTime() - new Date(booking.checkIn).getTime()) /
            86_400_000
        )
    );

    return (
        <>
            <Card>
                <CardContent className="p-4 space-y-4">
                    {/* Hotel name + status badge */}
                    <div className="flex items-start justify-between gap-2">
                        <div>
                            <p className="font-semibold">
                                {hotel?.name ?? (
                                    <span className="inline-block h-4 w-32 animate-pulse rounded bg-muted"/>
                                )}
                            </p>
                            <div className="mt-0.5 flex items-center gap-1 text-xs text-muted-foreground">
                                <MapPin className="h-3 w-3"/>
                                {hotel ? `${hotel.city}, ${hotel.country}` : "—"}
                            </div>
                        </div>
                        <span
                            className={`shrink-0 rounded-full px-2 py-0.5 text-xs font-medium capitalize ${STATUS_CLASS[booking.status]}`}
                        >
                            {booking.status.toLowerCase()}
                        </span>
                    </div>

                    {/* Dates */}
                    <div className="flex items-center gap-2 text-sm text-muted-foreground">
                        <Calendar className="h-4 w-4 shrink-0"/>
                        <span>
                            {new Date(booking.checkIn).toLocaleDateString()} →{" "}
                            {new Date(booking.checkOut).toLocaleDateString()}
                        </span>
                        <span className="text-xs">
                            ({nights} night{nights !== 1 ? "s" : ""})
                        </span>
                    </div>

                    {/* Total */}
                    <div className="flex items-center justify-between text-sm">
                        <span className="text-muted-foreground">Total paid</span>
                        <span className="font-semibold">${booking.totalPrice.toFixed(2)}</span>
                    </div>

                    {/* Special requests */}
                    {booking.specialRequests && (
                        <p className="border-t pt-2 text-xs text-muted-foreground">
                            <span className="font-medium">Request:</span> {booking.specialRequests}
                        </p>
                    )}

                    {/* Actions */}
                    {(canCancel || canReview) && (
                        <div className="flex gap-2 border-t pt-2">
                            {canCancel && (
                                <>
                                    <Button
                                        variant="outline"
                                        size="sm"
                                        disabled={cancelling}
                                        onClick={() => setCancelOpen(true)}
                                    >
                                        Cancel booking
                                    </Button>

                                    <AlertDialog open={cancelOpen} onOpenChange={setCancelOpen}>
                                        <AlertDialogContent>
                                            <AlertDialogHeader>
                                                <AlertDialogTitle>Cancel this booking?</AlertDialogTitle>
                                                <AlertDialogDescription>
                                                    This cannot be undone. Your booking at{" "}
                                                    <strong>{hotel?.name}</strong> will be cancelled.
                                                </AlertDialogDescription>
                                            </AlertDialogHeader>
                                            <AlertDialogFooter>
                                                <AlertDialogCancel>Keep booking</AlertDialogCancel>
                                                <AlertDialogAction
                                                    onClick={() => cancel(booking.id)}
                                                    className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
                                                >
                                                    Yes, cancel
                                                </AlertDialogAction>
                                            </AlertDialogFooter>
                                        </AlertDialogContent>
                                    </AlertDialog>
                                </>
                            )}

                            {canReview && (
                                <Button size="sm" onClick={() => setReviewOpen(true)}>
                                    Leave a review
                                </Button>
                            )}
                        </div>
                    )}
                </CardContent>
            </Card>

            {/* Review modal — only mounted when needed */}
            {canReview && (
                <ReviewModal
                    open={reviewOpen}
                    onOpenChange={setReviewOpen}
                    hotelId={booking.hotelId}
                    hotelName={hotel?.name ?? ""}
                    defaultStayDate={booking.checkOut}
                />
            )}
        </>
    );
}
