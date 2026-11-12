"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { queryKeys } from "./queryKeys";
import type { AvailabilityResponse, BookingResponse, BookingStatus, CreateBookingRequest, Page, } from "@/types";

// ── API calls ──────────────────────────────────────────────────────────────
const fetchMyBookings = (page = 0) =>
    api.get<Page<BookingResponse>>("/api/v1/bookings/my", {params: {page}}).then((r) => r.data);

const fetchHotelBookings = (hotelId: string, status?: BookingStatus) =>
    api
        .get<BookingResponse[]>(`/api/v1/bookings/hotel/${hotelId}`, {
            params: status ? {status} : undefined,
        })
        .then((r) => r.data);

const checkAvailabilityApi = (roomId: string, checkIn: string, checkOut: string) =>
    api
        .get<AvailabilityResponse>("/api/v1/bookings/availability", {
            params: {roomId, checkIn, checkOut},
        })
        .then((r) => r.data);

const createBookingApi = (data: CreateBookingRequest) =>
    api.post<BookingResponse>("/api/v1/bookings", data).then((r) => r.data);

const cancelBookingApi = (id: string) =>
    api.put<BookingResponse>(`/api/v1/bookings/${id}/cancel`).then((r) => r.data);

// ── Query hooks ────────────────────────────────────────────────────────────
export function useMyBookings(page = 0) {
    return useQuery({
        queryKey: queryKeys.bookings.mine(page),
        queryFn: () => fetchMyBookings(page),
    });
}

export function useHotelBookings(hotelId: string, status?: BookingStatus) {
    return useQuery({
        queryKey: queryKeys.bookings.hotel(hotelId),
        queryFn: () => fetchHotelBookings(hotelId, status),
        enabled: !!hotelId,
    });
}

export function useCheckAvailability(roomId: string, checkIn: string, checkOut: string) {
    return useQuery({
        queryKey: queryKeys.bookings.availability(roomId, checkIn, checkOut),
        queryFn: () => checkAvailabilityApi(roomId, checkIn, checkOut),
        enabled: !!roomId && !!checkIn && !!checkOut,
    });
}

// ── Mutation hooks ─────────────────────────────────────────────────────────
export function useCreateBooking() {
    const qc = useQueryClient();
    return useMutation({
        mutationFn: createBookingApi,
        onSuccess: () => qc.invalidateQueries({queryKey: ["bookings"]}),
    });
}

export function useCancelBooking() {
    const qc = useQueryClient();
    return useMutation({
        mutationFn: cancelBookingApi,
        onSuccess: () => qc.invalidateQueries({queryKey: ["bookings"]}),
    });
}
