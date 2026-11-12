import { CheckCircle, Star } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import type { ReviewResponse } from "@/types";

const SENTIMENT_CLASS: Record<NonNullable<ReviewResponse["sentiment"]>, string> = {
    POSITIVE: "bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400",
    NEUTRAL: "bg-muted text-muted-foreground",
    NEGATIVE: "bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400",
};

interface ReviewCardProps {
    review: ReviewResponse;
}

export function ReviewCard({review}: ReviewCardProps) {
    return (
        <Card>
            <CardContent className="p-4 space-y-3">
                {/* Top row: guest + rating + badges */}
                <div className="flex items-start justify-between gap-2">
                    <div>
                        <p className="font-medium text-sm">{review.guestName}</p>
                        <div className="flex mt-0.5">
                            {Array.from({length: 5}).map((_, i) => (
                                <Star
                                    key={i}
                                    className={`w-3.5 h-3.5 ${
                                        i < review.rating
                                            ? "fill-yellow-400 text-yellow-400"
                                            : "fill-muted text-muted-foreground/30"
                                    }`}
                                />
                            ))}
                        </div>
                    </div>

                    <div className="flex items-center gap-1.5 shrink-0">
                        {review.verified && (
                            <Badge variant="secondary" className="text-xs gap-1 py-0">
                                <CheckCircle className="w-3 h-3"/> Verified
                            </Badge>
                        )}
                        {review.sentiment && (
                            <span
                                className={`text-xs px-2 py-0.5 rounded-full font-medium capitalize ${SENTIMENT_CLASS[review.sentiment]}`}>
                                {review.sentiment.toLowerCase()}
                            </span>
                        )}
                    </div>
                </div>

                {/* Title + body */}
                <div>
                    <p className="font-semibold text-sm">{review.title}</p>
                    <p className="text-sm text-muted-foreground leading-relaxed mt-1">{review.body}</p>
                </div>

                {/* AI tags */}
                {review.tags?.length > 0 && (
                    <div className="flex flex-wrap gap-1">
                        {review.tags.map((tag) => (
                            <Badge key={tag} variant="outline" className="text-xs capitalize">{tag}</Badge>
                        ))}
                    </div>
                )}

                {/* Stay date */}
                <p className="text-xs text-muted-foreground">
                    Stayed{" "}
                    {new Date(review.stayDate).toLocaleDateString("en-US", {
                        month: "long",
                        year: "numeric",
                    })}
                </p>
            </CardContent>
        </Card>
    );
}
