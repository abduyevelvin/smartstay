"use client";

import { useState } from "react";
import Link from "next/link";
import { BedDouble, BookOpen, Building2, MapPin, Pencil, Star, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { AddRoomModal } from "./AddRoomModal";
import { EditHotelModal } from "./EditHotelModal";
import { HotelBookingsModal } from "./HotelBookingsModal";
import { useDeleteHotel } from "@/lib/hooks/useHotels";
import type { HotelResponse } from "@/types";

interface ManagerHotelCardProps {
    hotel: HotelResponse;
}

export function ManagerHotelCard({hotel}: ManagerHotelCardProps) {
    const [roomModal, setRoomModal] = useState(false);
    const [editModal, setEditModal] = useState(false);
    const [bookingsModal, setBookingsModal] = useState(false);
    const [confirmDelete, setConfirmDelete] = useState(false);

    const {mutate: deleteHotel, isPending: isDeleting} = useDeleteHotel();

    const handleDelete = () => {
        if (!confirmDelete) {
            setConfirmDelete(true);
            return;
        }
        deleteHotel(hotel.id, {onSuccess: () => setConfirmDelete(false)});
    };

    return (
        <>
            <div className="rounded-xl border bg-card shadow-sm flex flex-col">
                {/* Clickable top section → hotel detail */}
                <Link href={`/hotels/${hotel.id}`} className="flex flex-col gap-3 p-5 group">
                    {/* Header */}
                    <div className="flex items-start justify-between gap-3">
                        <div className="flex items-center gap-2 min-w-0">
                            <Building2 className="h-5 w-5 shrink-0 text-primary"/>
                            <h3 className="truncate font-semibold text-base leading-tight group-hover:underline">
                                {hotel.name}
                            </h3>
                        </div>
                        <div className="flex items-center gap-0.5 shrink-0">
                            {Array.from({length: hotel.starRating}).map((_, i) => (
                                <Star key={i} className="h-3.5 w-3.5 fill-yellow-400 text-yellow-400"/>
                            ))}
                        </div>
                    </div>

                    {/* Location */}
                    <div className="flex items-center gap-1.5 text-sm text-muted-foreground">
                        <MapPin className="h-3.5 w-3.5 shrink-0"/>
                        <span className="truncate">
                            {hotel.address}, {hotel.city}, {hotel.country}
                        </span>
                    </div>

                    {/* Stats row */}
                    <div className="flex items-center gap-4 text-xs text-muted-foreground">
                        <span>
                            ⭐ {hotel.averageRating > 0 ? hotel.averageRating.toFixed(1) : "—"} ({hotel.reviewCount} reviews)
                        </span>
                        <span>
                            Check-in {hotel.checkInTime} · Check-out {hotel.checkOutTime}
                        </span>
                    </div>

                    {/* Amenities */}
                    {hotel.amenities.length > 0 && (
                        <div className="flex flex-wrap gap-1.5">
                            {hotel.amenities.slice(0, 5).map((a) => (
                                <span
                                    key={a}
                                    className="rounded-full bg-muted px-2.5 py-0.5 text-xs text-muted-foreground"
                                >
                                    {a}
                                </span>
                            ))}
                            {hotel.amenities.length > 5 && (
                                <span className="rounded-full bg-muted px-2.5 py-0.5 text-xs text-muted-foreground">
                                    +{hotel.amenities.length - 5} more
                                </span>
                            )}
                        </div>
                    )}
                </Link>

                {/* Actions — outside the Link so clicks don't navigate */}
                <div className="flex flex-wrap items-center gap-2 px-5 py-3 border-t">
                    <Button
                        size="sm"
                        variant="outline"
                        className="flex items-center gap-1.5"
                        onClick={() => setRoomModal(true)}
                    >
                        <BedDouble className="h-3.5 w-3.5"/>
                        Add Room
                    </Button>

                    <Button
                        size="sm"
                        variant="outline"
                        className="flex items-center gap-1.5"
                        onClick={() => setEditModal(true)}
                    >
                        <Pencil className="h-3.5 w-3.5"/>
                        Edit
                    </Button>

                    <Button
                        size="sm"
                        variant="outline"
                        className="flex items-center gap-1.5"
                        onClick={() => setBookingsModal(true)}
                    >
                        <BookOpen className="h-3.5 w-3.5"/>
                        View Bookings
                    </Button>

                    <div className="ml-auto">
                        {confirmDelete ? (
                            <div className="flex items-center gap-2">
                                <span className="text-xs text-destructive font-medium">Confirm?</span>
                                <Button
                                    size="sm"
                                    variant="destructive"
                                    disabled={isDeleting}
                                    onClick={handleDelete}
                                >
                                    {isDeleting ? "Deleting…" : "Yes, delete"}
                                </Button>
                                <Button
                                    size="sm"
                                    variant="ghost"
                                    onClick={() => setConfirmDelete(false)}
                                >
                                    Cancel
                                </Button>
                            </div>
                        ) : (
                            <Button
                                size="sm"
                                variant="ghost"
                                className="text-destructive hover:text-destructive hover:bg-destructive/10"
                                onClick={handleDelete}
                            >
                                <Trash2 className="h-3.5 w-3.5"/>
                            </Button>
                        )}
                    </div>
                </div>
            </div>

            <AddRoomModal
                open={roomModal}
                onOpenChange={setRoomModal}
                hotelId={hotel.id}
                hotelName={hotel.name}
            />

            <EditHotelModal
                open={editModal}
                onOpenChange={setEditModal}
                hotel={hotel}
            />

            <HotelBookingsModal
                open={bookingsModal}
                onOpenChange={setBookingsModal}
                hotelId={hotel.id}
                hotelName={hotel.name}
            />
        </>
    );
}
