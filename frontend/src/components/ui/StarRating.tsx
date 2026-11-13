import { Star } from "lucide-react";

interface StarRatingProps {
    /** Numeric rating, supports 0.5 increments (e.g. 4.5) */
    rating: number;
    /** Icon size class — defaults to "w-4 h-4" */
    className?: string;
}

/**
 * Renders a row of up to 5 stars supporting half-star increments.
 * Full stars are filled yellow, the half star clips the left half,
 * and empty stars show a faint yellow outline.
 */
export function StarRating({rating, className = "w-4 h-4"}: StarRatingProps) {
    const clamped = Math.min(5, Math.max(0, rating));
    const fullStars = Math.floor(clamped);
    const hasHalf = clamped % 1 >= 0.25 && clamped % 1 < 0.75;
    const emptyStars = 5 - fullStars - (hasHalf ? 1 : 0);

    // Round-up edge: e.g. 4.8 → show 5 full stars
    const effectiveFull = clamped % 1 >= 0.75 ? fullStars + 1 : fullStars;
    const effectiveEmpty = 5 - effectiveFull - (hasHalf ? 1 : 0);

    return (
        <div className="flex items-center">
            {Array.from({length: effectiveFull}).map((_, i) => (
                <Star key={`full-${i}`} className={`${className} fill-yellow-400 text-yellow-400`}/>
            ))}
            {hasHalf && (
                <span className={`relative inline-block ${className}`}>
                    {/* empty outline underneath */}
                    <Star className={`${className} text-yellow-300`}/>
                    {/* filled left half clipped on top */}
                    <span className="absolute inset-0 overflow-hidden w-1/2">
                        <Star className={`${className} fill-yellow-400 text-yellow-400`}/>
                    </span>
                </span>
            )}
            {Array.from({length: effectiveEmpty}).map((_, i) => (
                <Star key={`empty-${i}`} className={`${className} text-yellow-300`}/>
            ))}
        </div>
    );
}
