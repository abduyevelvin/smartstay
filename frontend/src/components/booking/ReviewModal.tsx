"use client";

import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Star } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { useCreateReview } from "@/lib/hooks/useReviews";
import { getErrorMessage } from "@/lib/error";

// ── Star picker ───────────────────────────────────────────────────────────────
function StarPicker({value, onChange}: { value: number; onChange: (v: number) => void }) {
    return (
        <div className="flex gap-1">
            {[1, 2, 3, 4, 5].map((star) => (
                <button
                    key={star}
                    type="button"
                    onClick={() => onChange(star)}
                    className="focus:outline-none"
                >
                    <Star
                        className={`h-6 w-6 transition-colors ${
                            star <= value
                                ? "fill-yellow-400 text-yellow-400"
                                : "text-muted-foreground/40 hover:text-yellow-300"
                        }`}
                    />
                </button>
            ))}
        </div>
    );
}

// ── Schema ────────────────────────────────────────────────────────────────────
const schema = z.object({
    rating: z.number().min(1, "Please select a rating").max(5),
    title: z.string().min(1, "Title is required"),
    body: z.string().min(10, "Please write at least 10 characters"),
    stayDate: z.string().min(1, "Stay date is required"),
});

type FormData = z.infer<typeof schema>;

// ── Props ─────────────────────────────────────────────────────────────────────
interface ReviewModalProps {
    open: boolean;
    onOpenChange: (open: boolean) => void;
    hotelId: string;
    hotelName: string;
    defaultStayDate: string; // ISO from booking.checkOut
}

// ── Component ─────────────────────────────────────────────────────────────────
export function ReviewModal({
                                open,
                                onOpenChange,
                                hotelId,
                                hotelName,
                                defaultStayDate,
                            }: ReviewModalProps) {
    const {mutate: createReview, isPending, error} = useCreateReview();

    const form = useForm<FormData>({
        resolver: zodResolver(schema),
        defaultValues: {
            rating: 0,
            title: "",
            body: "",
            stayDate: defaultStayDate.split("T")[0],
        },
    });

    const onSubmit = (data: FormData) => {
        createReview(
            {hotelId, ...data},
            {
                onSuccess: () => {
                    onOpenChange(false);
                    form.reset();
                },
            }
        );
    };

    return (
        <Dialog open={open} onOpenChange={onOpenChange}>
            <DialogContent className="sm:max-w-md">
                <DialogHeader>
                    <DialogTitle>Review {hotelName}</DialogTitle>
                </DialogHeader>

                <Form {...form}>
                    <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
                        {/* Rating */}
                        <FormField
                            control={form.control}
                            name="rating"
                            render={({field}) => (
                                <FormItem>
                                    <FormLabel>Rating</FormLabel>
                                    <FormControl>
                                        <StarPicker value={field.value} onChange={field.onChange}/>
                                    </FormControl>
                                    <FormMessage/>
                                </FormItem>
                            )}
                        />

                        {/* Title */}
                        <FormField
                            control={form.control}
                            name="title"
                            render={({field}) => (
                                <FormItem>
                                    <FormLabel>Title</FormLabel>
                                    <FormControl>
                                        <Input placeholder="Summarise your stay…" {...field} />
                                    </FormControl>
                                    <FormMessage/>
                                </FormItem>
                            )}
                        />

                        {/* Body */}
                        <FormField
                            control={form.control}
                            name="body"
                            render={({field}) => (
                                <FormItem>
                                    <FormLabel>Your review</FormLabel>
                                    <FormControl>
                                        <Textarea
                                            rows={4}
                                            placeholder="Tell others about your experience…"
                                            {...field}
                                        />
                                    </FormControl>
                                    <FormMessage/>
                                </FormItem>
                            )}
                        />

                        {/* Stay date */}
                        <FormField
                            control={form.control}
                            name="stayDate"
                            render={({field}) => (
                                <FormItem>
                                    <FormLabel>Stay date</FormLabel>
                                    <FormControl>
                                        <Input type="date" {...field} />
                                    </FormControl>
                                    <FormMessage/>
                                </FormItem>
                            )}
                        />

                        {error && (
                            <p className="text-sm text-destructive">{getErrorMessage(error)}</p>
                        )}

                        <div className="flex justify-end gap-2 pt-1">
                            <Button
                                type="button"
                                variant="outline"
                                onClick={() => onOpenChange(false)}
                            >
                                Cancel
                            </Button>
                            <Button type="submit" disabled={isPending}>
                                {isPending ? "Submitting…" : "Submit review"}
                            </Button>
                        </div>
                    </form>
                </Form>
            </DialogContent>
        </Dialog>
    );
}
