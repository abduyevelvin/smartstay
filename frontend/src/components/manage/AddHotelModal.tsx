"use client";

import type { Resolver } from "react-hook-form";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";

import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { useCreateHotel } from "@/lib/hooks/useHotels";
import { getErrorMessage } from "@/lib/error";

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
    checkInTime: z.string().min(1, "Check-in time is required"),
    checkOutTime: z.string().min(1, "Check-out time is required"),
});

type FormData = z.infer<typeof schema>;

interface AddHotelModalProps {
    open: boolean;
    onOpenChange: (open: boolean) => void;
}

export function AddHotelModal({open, onOpenChange}: AddHotelModalProps) {
    const {mutate: createHotel, isPending, error} = useCreateHotel();

    const form = useForm<FormData>({
        resolver: zodResolver(schema) as Resolver<FormData>,
        defaultValues: {
            name: "", description: "", city: "", country: "",
            address: "", starRating: 3, amenities: "",
            checkInTime: "14:00", checkOutTime: "12:00",
        },
    });

    const onSubmit = (data: FormData) => {
        createHotel(
            {...data, amenities: parseAmenities(data.amenities)},
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
            <DialogContent className="sm:max-w-lg max-h-[90vh] overflow-y-auto">
                <DialogHeader>
                    <DialogTitle>Add new hotel</DialogTitle>
                </DialogHeader>

                <Form {...form}>
                    <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
                        <FormField control={form.control} name="name" render={({field}) => (
                            <FormItem>
                                <FormLabel>Hotel name</FormLabel>
                                <FormControl><Input placeholder="Grand Palace Hotel" {...field} /></FormControl>
                                <FormMessage/>
                            </FormItem>
                        )}/>

                        <FormField control={form.control} name="description" render={({field}) => (
                            <FormItem>
                                <FormLabel>Description</FormLabel>
                                <FormControl><Textarea rows={3}
                                                       placeholder="Describe your hotel…" {...field} /></FormControl>
                                <FormMessage/>
                            </FormItem>
                        )}/>

                        <div className="grid grid-cols-2 gap-4">
                            <FormField control={form.control} name="city" render={({field}) => (
                                <FormItem>
                                    <FormLabel>City</FormLabel>
                                    <FormControl><Input placeholder="Paris" {...field} /></FormControl>
                                    <FormMessage/>
                                </FormItem>
                            )}/>
                            <FormField control={form.control} name="country" render={({field}) => (
                                <FormItem>
                                    <FormLabel>Country</FormLabel>
                                    <FormControl><Input placeholder="France" {...field} /></FormControl>
                                    <FormMessage/>
                                </FormItem>
                            )}/>
                        </div>

                        <FormField control={form.control} name="address" render={({field}) => (
                            <FormItem>
                                <FormLabel>Address</FormLabel>
                                <FormControl><Input placeholder="123 Rue de Rivoli" {...field} /></FormControl>
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
                                    <FormControl><Input placeholder="14:00" {...field} /></FormControl>
                                    <FormMessage/>
                                </FormItem>
                            )}/>
                            <FormField control={form.control} name="checkOutTime" render={({field}) => (
                                <FormItem>
                                    <FormLabel>Check-out</FormLabel>
                                    <FormControl><Input placeholder="12:00" {...field} /></FormControl>
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

                        {error && <p className="text-sm text-destructive">{getErrorMessage(error)}</p>}

                        <div className="flex justify-end gap-2 pt-1">
                            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>Cancel</Button>
                            <Button type="submit" disabled={isPending}>
                                {isPending ? "Creating…" : "Create hotel"}
                            </Button>
                        </div>
                    </form>
                </Form>
            </DialogContent>
        </Dialog>
    );
}
