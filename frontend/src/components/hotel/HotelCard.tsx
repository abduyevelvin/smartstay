import Link from "next/link";
import { MapPin } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { StarRating } from "@/components/ui/StarRating";
import type { HotelResponse } from "@/types";

interface HotelCardProps {
    hotel: HotelResponse;
}

export function HotelCard({hotel}: HotelCardProps) {
    return (
        <Link href={`/hotels/${hotel.id}`} className="block group">
            <Card className="overflow-hidden transition-shadow group-hover:shadow-md h-full">
                {/* Image */}
                <div className="h-48 bg-muted flex items-center justify-center overflow-hidden">
                    {hotel.images?.[0] ? (
                        <img
                            src={hotel.images[0]}
                            alt={hotel.name}
                            className="w-full h-full object-cover transition-transform group-hover:scale-105"
                        />
                    ) : (
                        <span className="text-muted-foreground text-sm">No image</span>
                    )}
                </div>

                <CardContent className="p-4 space-y-2">
                    {/* Name + stars */}
                    <div className="flex items-start justify-between gap-2">
                        <h3 className="font-semibold text-sm leading-snug line-clamp-2">
                            {hotel.name}
                        </h3>
                        <div className="shrink-0 mt-0.5">
                            <StarRating rating={hotel.starRating} className="w-3 h-3"/>
                        </div>
                    </div>

                    {/* Location */}
                    <div className="flex items-center gap-1 text-muted-foreground">
                        <MapPin className="w-3.5 h-3.5 shrink-0"/>
                        <span className="text-xs truncate">{hotel.city}, {hotel.country}</span>
                    </div>

                    {/* Rating */}
                    {hotel.averageRating > 0 && (
                        <div className="flex items-center gap-1.5">
                            <Badge variant="secondary" className="text-xs px-1.5 py-0">
                                ★ {hotel.averageRating.toFixed(1)}
                            </Badge>
                            <span className="text-xs text-muted-foreground">
                                ({hotel.reviewCount} review{hotel.reviewCount !== 1 ? "s" : ""})
                            </span>
                        </div>
                    )}

                    {/* Top amenities */}
                    {hotel.amenities?.length > 0 && (
                        <div className="flex flex-wrap gap-1 pt-1">
                            {hotel.amenities.slice(0, 3).map((a) => (
                                <Badge key={a} variant="outline" className="text-xs px-1.5 py-0">
                                    {a}
                                </Badge>
                            ))}
                            {hotel.amenities.length > 3 && (
                                <Badge variant="outline" className="text-xs px-1.5 py-0">
                                    +{hotel.amenities.length - 3} more
                                </Badge>
                            )}
                        </div>
                    )}
                </CardContent>
            </Card>
        </Link>
    );
}
