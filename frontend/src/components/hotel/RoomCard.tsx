import Link from "next/link";
import { Users } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import type { RoomResponse } from "@/types";

const TYPE_LABELS: Record<RoomResponse["type"], string> = {
    SINGLE: "Single Room",
    DOUBLE: "Double Room",
    SUITE: "Suite",
    PENTHOUSE: "Penthouse",
};

interface RoomCardProps {
    room: RoomResponse;
    hotelId: string;
    canBook: boolean; // true only for authenticated GUESTs
}

export function RoomCard({room, hotelId, canBook}: RoomCardProps) {
    return (
        <Card className="flex flex-col">
            <CardContent className="p-4 flex flex-col flex-1 gap-3">
                {/* Header: type + price */}
                <div className="flex items-start justify-between">
                    <div>
                        <h4 className="font-semibold">{TYPE_LABELS[room.type]}</h4>
                        <p className="text-xs text-muted-foreground">
                            Room {room.roomNumber} · Floor {room.floor}
                        </p>
                    </div>
                    <div className="text-right shrink-0">
                        <p className="text-lg font-bold">${room.pricePerNight}</p>
                        <p className="text-xs text-muted-foreground">/ night</p>
                    </div>
                </div>

                {/* Occupancy */}
                <div className="flex items-center gap-1.5 text-sm text-muted-foreground">
                    <Users className="w-4 h-4"/>
                    <span>Up to {room.maxOccupancy} guest{room.maxOccupancy !== 1 ? "s" : ""}</span>
                </div>

                {/* Room amenities */}
                {room.amenities?.length > 0 && (
                    <div className="flex flex-wrap gap-1">
                        {room.amenities.map((a) => (
                            <Badge key={a} variant="outline" className="text-xs">{a}</Badge>
                        ))}
                    </div>
                )}

                {/* Book button — pushed to bottom */}
                <div className="mt-auto pt-2">
                    {!room.available ? (
                        <Badge variant="destructive" className="w-full justify-center py-1.5">
                            Not Available
                        </Badge>
                    ) : canBook ? (
                        <Link
                            href={`/bookings/new?hotelId=${hotelId}&roomId=${room.id}&price=${room.pricePerNight}`}
                        >
                            <Button size="sm" className="w-full">Book this room</Button>
                        </Link>
                    ) : (
                        <Link href="/login">
                            <Button size="sm" variant="outline" className="w-full">
                                Sign in to book
                            </Button>
                        </Link>
                    )}
                </div>
            </CardContent>
        </Card>
    );
}
