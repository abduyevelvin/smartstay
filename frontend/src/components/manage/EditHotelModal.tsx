"use client";

import { useEffect, useState } from "react";
import type { Resolver } from "react-hook-form";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { ImageIcon } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { AxiosError } from "axios";
import { useUpdateHotel } from "@/lib/hooks/useHotels";
import { getErrorMessage } from "@/lib/error";
import type { HotelResponse } from "@/types";

const parseAmenities = (s?: string) =>
    s ? s.split(",").map((a) => a.trim()).filter(Boolean) : [];

const schema = z.object({
    name: z.string().min(1, "Name is required"),
    description: z.string().min(1, "Description is required"),
    city: z.string().min(1, "City is required"),
    country: z.string().min(1, "Country is required"),
    address: z.string().min(1, "Address is required"),
    starRating: z.coerce.number().min(1).max(5),
    amenities: z.string().optional(),
    imageUrl: z.string().url("Must be a valid URL").optional().or(z.literal("")),
    checkInTime: z.string().min(1, "Check-in time is required"),
    checkOutTime: z.string().min(1, "Check-out time is required"),
});

type FormData = z.infer<typeof schema>;

interface EditHotelModalProps {
    hotel: HotelResponse;
    open: boolean;
    onOpenChange: (open: boolean) => void;
}

export function EditHotelModal({hotel, open, onOpenChange}: EditHotelModalProps) {
    const {mutate: updateHotel, isPending, error} = useUpdateHotel();
    const [imgError, setImgError] = useState(false);

    const form = useForm<FormData>({
        resolver: zodResolver(schema) as Resolver<FormData>,
        defaultValues: {
            name: hotel.name,
            description: hotel.description,
            city: hotel.city,
            country: hotel.country,
            address: hotel.address,
            starRating: hotel.starRating,
            amenities: hotel.amenities?.join(", ") ?? "",
            imageUrl: hotel.images?.[0] ?? "",
            checkInTime: hotel.checkInTime,
            checkOutTime: hotel.checkOutTime,
        },
    });

    // Re-populate when the hotel prop changes (e.g. after a refetch)
    useEffect(() => {
        form.reset({
            name: hotel.name,
            description: hotel.description,
            city: hotel.city,
            country: hotel.country,
            address: hotel.address,
            starRating: hotel.starRating,
            amenities: hotel.amenities?.join(", ") ?? "",
            imageUrl: hotel.images?.[0] ?? "",
            checkInTime: hotel.checkInTime,
            checkOutTime: hotel.checkOutTime,
        });
        setImgError(false);
    }, [hotel, form]);

    const imageUrl = form.watch("imageUrl");

    const onSubmit = (data: FormData) => {
        const images = data.imageUrl ? [data.imageUrl] : [];
        updateHotel(
            {
                id: hotel.id,
                data: {
                    ...data,
                    amenities: parseAmenities(data.amenities),
                    images,
                },
            },
            {
                onSuccess: () => onOpenChange(false),
            }
        );
    };

    return (
        <Dialog open={open} onOpenChange={onOpenChange}>
            <DialogContent className="sm:max-w-lg max-h-[90vh] overflow-y-auto">
                <DialogHeader>
                    <DialogTitle>Edit hotel</DialogTitle>
                </DialogHeader>

                <Form {...form}>
                    <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
                        <FormField control={form.control} name="name" render={({field}) => (
                            <FormItem>
                                <FormLabel>Hotel name</FormLabel>
                                <FormControl><Input {...field} /></FormControl>
                                <FormMessage/>
                            </FormItem>
                        )}/>

                        <FormField control={form.control} name="description" render={({field}) => (
                            <FormItem>
                                <FormLabel>Description</FormLabel>
                                <FormControl><Textarea rows={3} {...field} /></FormControl>
                                <FormMessage/>
                            </FormItem>
                        )}/>

                        <div className="grid grid-cols-2 gap-4">
                            <FormField control={form.control} name="city" render={({field}) => (
                                <FormItem>
                                    <FormLabel>City</FormLabel>
                                    <FormControl><Input {...field} /></FormControl>
                                    <FormMessage/>
                                </FormItem>
                            )}/>
                            <FormField control={form.control} name="country" render={({field}) => (
                                <FormItem>
                                    <FormLabel>Country</FormLabel>
                                    <FormControl><Input {...field} /></FormControl>
                                    <FormMessage/>
                                </FormItem>
                            )}/>
                        </div>

                        <FormField control={form.control} name="address" render={({field}) => (
                            <FormItem>
                                <FormLabel>Address</FormLabel>
                                <FormControl><Input {...field} /></FormControl>
                                <FormMessage/>
                            </FormItem>
                        )}/>

                        <div className="grid grid-cols-3 gap-4">
                            <FormField control={form.control} name="starRating" render={({field}) => (
                                <FormItem>
                                    <FormLabel>Stars (1–5)</FormLabel>
                                    <FormControl><Input type="number" min={1} max={5} {...field} /></FormControl>
                                    <FormMessage/>
                                </FormItem>
                            )}/>
                            <FormField control={form.control} name="checkInTime" render={({field}) => (
                                <FormItem>
                                    <FormLabel>Check-in</FormLabel>
                                    <FormControl><Input {...field} /></FormControl>
                                    <FormMessage/>
                                </FormItem>
                            )}/>
                            <FormField control={form.control} name="checkOutTime" render={({field}) => (
                                <FormItem>
                                    <FormLabel>Check-out</FormLabel>
                                    <FormControl><Input {...field} /></FormControl>
                                    <FormMessage/>
                                </FormItem>
                            )}/>
                        </div>

                        <FormField control={form.control} name="amenities" render={({field}) => (
                            <FormItem>
                                <FormLabel>Amenities <span
                                    className="text-muted-foreground font-normal">(comma-separated)</span></FormLabel>
                                <FormControl><Input placeholder="WiFi, Pool, Gym, Spa" {...field} /></FormControl>
                                <FormMessage/>
                            </FormItem>
                        )}/>

                        {/* Image URL + live preview */}
                        <FormField control={form.control} name="imageUrl" render={({field}) => (
                            <FormItem>
                                <FormLabel>Main image <span
                                    className="text-muted-foreground font-normal">(URL, optional)</span></FormLabel>
                                <FormControl>
                                    <Input
                                        placeholder="https://example.com/hotel.jpg"
                                        {...field}
                                        onChange={(e) => {
                                            setImgError(false);
                                            field.onChange(e);
                                        }}
                                    />
                                </FormControl>
                                <FormMessage/>
                                {imageUrl && !imgError && (
                                    <div className="mt-2 h-36 rounded-md border bg-muted overflow-hidden">
                                        <img
                                            src={imageUrl}
                                            alt="Preview"
                                            className="w-full h-full object-cover"
                                            onError={() => setImgError(true)}
                                        />
                                    </div>
                                )}
                                {imageUrl && imgError && (
                                    <div className="mt-2 h-36 rounded-md border bg-muted flex flex-col items-center justify-center gap-1 text-muted-foreground">
                                        <ImageIcon className="w-6 h-6"/>
                                        <span className="text-xs">Could not load image</span>
                                    </div>
                                )}
                            </FormItem>
                        )}/>

                        {error && (
                            <p className="text-sm text-destructive">
                                {getErrorMessage(error instanceof AxiosError ? error : error)}
                            </p>
                        )}

                        <div className="flex justify-end gap-2 pt-1">
                            <Button type="button" variant="outline"
                                    onClick={() => onOpenChange(false)}>Cancel</Button>
                            <Button type="submit" disabled={isPending}>
                                {isPending ? "Saving…" : "Save changes"}
                            </Button>
                        </div>
                    </form>
                </Form>
            </DialogContent>
        </Dialog>
    );
}
