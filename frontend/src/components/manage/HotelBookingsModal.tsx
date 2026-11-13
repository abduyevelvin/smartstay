"use client";

import { Calendar, Mail } from "lucide-react";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { useHotelBookings } from "@/lib/hooks/useBookings";

const STATUS_CLASS: Record<string, string> = {
    CONFIRMED: "bg-green-100 text-green-700",
    CANCELLED: "bg-gray-100 text-gray-500",
};

interface HotelBookingsModalProps {
    open: boolean;
    onOpenChange: (open: boolean) => void;
    hotelId: string;
    hotelName: string;
}

export function HotelBookingsModal({open, onOpenChange, hotelId, hotelName}: HotelBookingsModalProps) {
    const {data: bookings, isLoading} = useHotelBookings(hotelId);

    return (
        <Dialog open={open} onOpenChange={onOpenChange}>
            <DialogContent className="sm:max-w-lg max-h-[80vh] overflow-y-auto">
                <DialogHeader>
                    <DialogTitle>Bookings — {hotelName}</DialogTitle>
                </DialogHeader>

                {isLoading && (
                    <div className="space-y-3">
                        {[1, 2, 3].map((i) => (
                            <div key={i} className="h-20 animate-pulse rounded-lg bg-muted"/>
                        ))}
                    </div>
                )}

                {!isLoading && !bookings?.length && (
                    <p className="py-8 text-center text-sm text-muted-foreground">
                        No bookings for this hotel yet.
                    </p>
                )}

                {!isLoading && !!bookings?.length && (
                    <div className="space-y-3">
                        {bookings.map((b) => (
                            <div key={b.id} className="rounded-lg border p-3 space-y-1.5">
                                <div className="flex items-center justify-between">
                                    <div className="flex items-center gap-1.5 text-sm">
                                        <Mail className="h-3.5 w-3.5 text-muted-foreground"/>
                                        <span className="font-medium">{b.guestEmail}</span>
                                    </div>
                                    <span
                                        className={`rounded-full px-2 py-0.5 text-xs font-medium capitalize ${STATUS_CLASS[b.status] ?? ""}`}>
                                        {b.status.toLowerCase()}
                                    </span>
                                </div>
                                <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                                    <Calendar className="h-3.5 w-3.5"/>
                                    <span>
                                        {new Date(b.checkIn).toLocaleDateString()} →{" "}
                                        {new Date(b.checkOut).toLocaleDateString()}
                                    </span>
                                </div>
                                <div className="flex justify-between text-xs text-muted-foreground">
                                    <span>Room: {b.roomId.slice(-6)}</span>
                                    <span className="font-medium text-foreground">${b.totalPrice.toFixed(2)}</span>
                                </div>
                            </div>
                        ))}
                    </div>
                )}
            </DialogContent>
        </Dialog>
    );
}
