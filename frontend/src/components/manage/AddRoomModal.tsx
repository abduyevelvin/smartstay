"use client";

import type { Resolver } from "react-hook-form";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";

import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useAddRoom } from "@/lib/hooks/useHotels";
import { getErrorMessage } from "@/lib/error";

const parseAmenities = (s?: string) =>
    s ? s.split(",").map((a) => a.trim()).filter(Boolean) : [];

const schema = z.object({
    roomNumber: z.string().min(1, "Room number is required"),
    type: z.enum(["SINGLE", "DOUBLE", "SUITE", "PENTHOUSE"]),
    pricePerNight: z.coerce.number().min(1, "Price must be at least 1"),
    maxOccupancy: z.coerce.number().min(1).max(20),
    floor: z.coerce.number().min(0),
    amenities: z.string().optional(),
});

type FormData = z.infer<typeof schema>;

interface AddRoomModalProps {
    open: boolean;
    onOpenChange: (open: boolean) => void;
    hotelId: string;
    hotelName: string;
}

export function AddRoomModal({open, onOpenChange, hotelId, hotelName}: AddRoomModalProps) {
    const {mutate: addRoom, isPending, error} = useAddRoom();

    const form = useForm<FormData>({
        resolver: zodResolver(schema) as Resolver<FormData>,
        defaultValues: {
            roomNumber: "", type: "DOUBLE",
            pricePerNight: 100, maxOccupancy: 2, floor: 1, amenities: "",
        },
    });

    const onSubmit = (data: FormData) => {
        addRoom(
            {hotelId, data: {...data, amenities: parseAmenities(data.amenities)}},
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
                    <DialogTitle>Add room — {hotelName}</DialogTitle>
                </DialogHeader>

                <Form {...form}>
                    <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
                        <div className="grid grid-cols-2 gap-4">
                            <FormField control={form.control} name="roomNumber" render={({field}) => (
                                <FormItem>
                                    <FormLabel>Room number</FormLabel>
                                    <FormControl><Input placeholder="101" {...field} /></FormControl>
                                    <FormMessage/>
                                </FormItem>
                            )}/>
                            <FormField control={form.control} name="floor" render={({field}) => (
                                <FormItem>
                                    <FormLabel>Floor</FormLabel>
                                    <FormControl><Input type="number" min={0} {...field} /></FormControl>
                                    <FormMessage/>
                                </FormItem>
                            )}/>
                        </div>

                        <FormField control={form.control} name="type" render={({field}) => (
                            <FormItem>
                                <FormLabel>Room type</FormLabel>
                                <Select onValueChange={field.onChange} defaultValue={field.value}>
                                    <FormControl>
                                        <SelectTrigger className="w-full">
                                            <SelectValue/>
                                        </SelectTrigger>
                                    </FormControl>
                                    <SelectContent>
                                        <SelectItem value="SINGLE">Single</SelectItem>
                                        <SelectItem value="DOUBLE">Double</SelectItem>
                                        <SelectItem value="SUITE">Suite</SelectItem>
                                        <SelectItem value="PENTHOUSE">Penthouse</SelectItem>
                                    </SelectContent>
                                </Select>
                                <FormMessage/>
                            </FormItem>
                        )}/>

                        <div className="grid grid-cols-2 gap-4">
                            <FormField control={form.control} name="pricePerNight" render={({field}) => (
                                <FormItem>
                                    <FormLabel>Price / night ($)</FormLabel>
                                    <FormControl><Input type="number" min={1} {...field} /></FormControl>
                                    <FormMessage/>
                                </FormItem>
                            )}/>
                            <FormField control={form.control} name="maxOccupancy" render={({field}) => (
                                <FormItem>
                                    <FormLabel>Max guests</FormLabel>
                                    <FormControl><Input type="number" min={1} max={20} {...field} /></FormControl>
                                    <FormMessage/>
                                </FormItem>
                            )}/>
                        </div>

                        <FormField control={form.control} name="amenities" render={({field}) => (
                            <FormItem>
                                <FormLabel>Amenities <span
                                    className="text-muted-foreground font-normal">(comma-separated)</span></FormLabel>
                                <FormControl><Input
                                    placeholder="TV, Air conditioning, Mini bar" {...field} /></FormControl>
                                <FormMessage/>
                            </FormItem>
                        )}/>

                        {error && <p className="text-sm text-destructive">{getErrorMessage(error)}</p>}

                        <div className="flex justify-end gap-2 pt-1">
                            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>Cancel</Button>
                            <Button type="submit" disabled={isPending}>
                                {isPending ? "Adding…" : "Add room"}
                            </Button>
                        </div>
                    </form>
                </Form>
            </DialogContent>
        </Dialog>
    );
}
