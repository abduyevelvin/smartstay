"use client";

import { useMemo } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import Link from "next/link";
import { ArrowLeft, Calendar, MapPin } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { useHotel } from "@/lib/hooks/useHotels";
import { useCreateBooking } from "@/lib/hooks/useBookings";
import { getErrorMessage } from "@/lib/error";

const WRAP = "mx-auto w-full max-w-screen-2xl px-4 sm:px-6 lg:px-10";

// ── Helpers ──────────────────────────────────────────────────────────────────
const todayStr = () => new Date().toISOString().split("T")[0];

function nightsBetween(checkIn: string, checkOut: string): number {
    if (!checkIn || !checkOut) return 0;
    const diff = new Date(checkOut).getTime() - new Date(checkIn).getTime();
    return Math.max(0, Math.floor(diff / 86_400_000));
}

// ── Zod schema ────────────────────────────────────────────────────────────────
const schema = z
    .object({
        checkIn: z.string().min(1, "Check-in date is required"),
        checkOut: z.string().min(1, "Check-out date is required"),
        specialRequests: z.string().optional(),
    })
    .refine((d) => !d.checkIn || !d.checkOut || d.checkOut > d.checkIn, {
        message: "Check-out must be after check-in",
        path: ["checkOut"],
    });

type FormData = z.infer<typeof schema>;

// ── Component ─────────────────────────────────────────────────────────────────
export function BookingForm() {
    const router = useRouter();
    const searchParams = useSearchParams();

    const hotelId = searchParams.get("hotelId") ?? "";
    const roomId = searchParams.get("roomId") ?? "";
    const pricePerNight = Number(searchParams.get("price") ?? 0);

    const {data: hotel, isLoading: hotelLoading} = useHotel(hotelId);
    const {mutate: createBooking, isPending, error} = useCreateBooking();

    const form = useForm<FormData>({
        resolver: zodResolver(schema),
        defaultValues: {checkIn: "", checkOut: "", specialRequests: ""},
    });

    const checkIn = form.watch("checkIn");
    const checkOut = form.watch("checkOut");
    const nights = useMemo(() => nightsBetween(checkIn, checkOut), [checkIn, checkOut]);
    const totalPrice = nights * pricePerNight;

    const onSubmit = (data: FormData) => {
        createBooking(
            {
                hotelId,
                roomId,
                checkIn: data.checkIn,
                checkOut: data.checkOut,
                specialRequests: data.specialRequests || undefined,
            },
            {onSuccess: () => router.push("/bookings")}
        );
    };

    // ── Guard: missing params ─────────────────────────────────────────────────
    if (!hotelId || !roomId || !pricePerNight) {
        return (
            <div className="flex min-h-[40vh] flex-col items-center justify-center gap-4">
                <p className="text-muted-foreground">Invalid booking link.</p>
                <Link href="/"><Button variant="outline">Back to search</Button></Link>
            </div>
        );
    }

    return (
        <div className={`${WRAP} py-8`}>
            {/* Back */}
            <Link
                href={`/hotels/${hotelId}`}
                className="mb-8 inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground transition-colors"
            >
                <ArrowLeft className="h-4 w-4"/> Back to hotel
            </Link>

            <h1 className="mb-8 text-2xl font-bold">Complete your booking</h1>

            <div className="grid grid-cols-1 gap-8 lg:grid-cols-3">

                {/* ── Form (2 cols) ─────────────────────────────────────────── */}
                <div className="lg:col-span-2">
                    <Card>
                        <CardHeader>
                            <CardTitle className="flex items-center gap-2 text-base">
                                <Calendar className="h-4 w-4"/> Select dates
                            </CardTitle>
                        </CardHeader>
                        <CardContent>
                            <Form {...form}>
                                <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-5">
                                    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                                        <FormField
                                            control={form.control}
                                            name="checkIn"
                                            render={({field}) => (
                                                <FormItem>
                                                    <FormLabel>Check-in</FormLabel>
                                                    <FormControl>
                                                        <Input type="date" min={todayStr()} {...field} />
                                                    </FormControl>
                                                    <FormMessage/>
                                                </FormItem>
                                            )}
                                        />
                                        <FormField
                                            control={form.control}
                                            name="checkOut"
                                            render={({field}) => (
                                                <FormItem>
                                                    <FormLabel>Check-out</FormLabel>
                                                    <FormControl>
                                                        <Input
                                                            type="date"
                                                            min={checkIn || todayStr()}
                                                            {...field}
                                                        />
                                                    </FormControl>
                                                    <FormMessage/>
                                                </FormItem>
                                            )}
                                        />
                                    </div>

                                    <FormField
                                        control={form.control}
                                        name="specialRequests"
                                        render={({field}) => (
                                            <FormItem>
                                                <FormLabel>
                                                    Special requests{" "}
                                                    <span className="font-normal text-muted-foreground">
                                                        (optional)
                                                    </span>
                                                </FormLabel>
                                                <FormControl>
                                                    <Textarea
                                                        rows={3}
                                                        placeholder="Early check-in, dietary requirements, accessibility needs…"
                                                        {...field}
                                                    />
                                                </FormControl>
                                                <FormMessage/>
                                            </FormItem>
                                        )}
                                    />

                                    {error && (
                                        <p className="text-sm text-destructive">
                                            {getErrorMessage(error)}
                                        </p>
                                    )}

                                    <Button
                                        type="submit"
                                        className="w-full"
                                        disabled={isPending || nights === 0}
                                    >
                                        {isPending ? "Confirming…" : "Confirm Booking"}
                                    </Button>
                                </form>
                            </Form>
                        </CardContent>
                    </Card>
                </div>

                {/* ── Summary sidebar (1 col) ───────────────────────────────── */}
                <div>
                    <Card className="sticky top-24">
                        <CardHeader className="pb-2">
                            <CardTitle className="text-base">Booking Summary</CardTitle>
                        </CardHeader>
                        <CardContent className="space-y-4 text-sm">
                            {/* Hotel info */}
                            {hotelLoading ? (
                                <div className="h-4 w-3/4 animate-pulse rounded bg-muted"/>
                            ) : (
                                <div className="flex items-start gap-2">
                                    <MapPin className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground"/>
                                    <div>
                                        <p className="font-medium">{hotel?.name}</p>
                                        <p className="text-xs text-muted-foreground">
                                            {hotel?.city}, {hotel?.country}
                                        </p>
                                    </div>
                                </div>
                            )}

                            {/* Price breakdown */}
                            <div className="border-t pt-3 space-y-1.5 text-muted-foreground">
                                <div className="flex justify-between">
                                    <span>
                                        ${pricePerNight} × {nights} night{nights !== 1 ? "s" : ""}
                                    </span>
                                    <span className="text-foreground font-medium">
                                        ${totalPrice.toFixed(2)}
                                    </span>
                                </div>
                            </div>

                            {/* Total */}
                            <div className="border-t pt-3 flex justify-between text-base font-semibold">
                                <span>Total</span>
                                <span>${totalPrice.toFixed(2)}</span>
                            </div>

                            {nights === 0 && (
                                <p className="text-center text-xs text-muted-foreground">
                                    Select dates to see total
                                </p>
                            )}
                        </CardContent>
                    </Card>
                </div>

            </div>
        </div>
    );
}
