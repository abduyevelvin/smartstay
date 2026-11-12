"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { queryKeys } from "./queryKeys";
import type {
    CreateHotelRequest,
    CreateRoomRequest,
    HotelResponse,
    HotelSearchParams,
    Page,
    RoomResponse,
    UpdateHotelRequest,
} from "@/types";

// ── API calls ──────────────────────────────────────────────────────────────
const fetchHotels = (params: HotelSearchParams) =>
    api.get<Page<HotelResponse>>("/api/v1/hotels", {params}).then((r) => r.data);

const fetchHotel = (id: string) =>
    api.get<HotelResponse>(`/api/v1/hotels/${id}`).then((r) => r.data);

const fetchRooms = (hotelId: string) =>
    api.get<RoomResponse[]>(`/api/v1/hotels/${hotelId}/rooms`).then((r) => r.data);

const fetchAvailableRooms = (hotelId: string) =>
    api.get<RoomResponse[]>(`/api/v1/hotels/${hotelId}/availability`).then((r) => r.data);

const createHotelApi = (data: CreateHotelRequest) =>
    api.post<HotelResponse>("/api/v1/hotels", data).then((r) => r.data);

const updateHotelApi = ({id, data}: { id: string; data: UpdateHotelRequest }) =>
    api.put<HotelResponse>(`/api/v1/hotels/${id}`, data).then((r) => r.data);

const deleteHotelApi = (id: string) => api.delete(`/api/v1/hotels/${id}`);

const addRoomApi = ({hotelId, data}: { hotelId: string; data: CreateRoomRequest }) =>
    api.post<RoomResponse>(`/api/v1/hotels/${hotelId}/rooms`, data).then((r) => r.data);

// ── Query hooks ────────────────────────────────────────────────────────────
export function useHotels(params: HotelSearchParams = {}) {
    return useQuery({
        queryKey: queryKeys.hotels.list(params),
        queryFn: () => fetchHotels(params),
    });
}

export function useHotel(id: string) {
    return useQuery({
        queryKey: queryKeys.hotels.detail(id),
        queryFn: () => fetchHotel(id),
        enabled: !!id,
    });
}

export function useRooms(hotelId: string) {
    return useQuery({
        queryKey: queryKeys.hotels.rooms(hotelId),
        queryFn: () => fetchRooms(hotelId),
        enabled: !!hotelId,
    });
}

export function useManagerHotels(managerId: string) {
    return useQuery({
        queryKey: queryKeys.hotels.manager(managerId),
        queryFn: () =>
            api
                .get<Page<HotelResponse>>("/api/v1/hotels", {params: {size: 100}})
                .then((r) => r.data.content.filter((h) => h.managerId === managerId)),
        enabled: !!managerId,
    });
}

export function useAvailableRooms(hotelId: string) {
    return useQuery({
        queryKey: queryKeys.hotels.availability(hotelId),
        queryFn: () => fetchAvailableRooms(hotelId),
        enabled: !!hotelId,
    });
}

// ── Mutation hooks ─────────────────────────────────────────────────────────
export function useCreateHotel() {
    const qc = useQueryClient();
    return useMutation({
        mutationFn: createHotelApi,
        onSuccess: () => qc.invalidateQueries({queryKey: ["hotels"]}),
    });
}

export function useUpdateHotel() {
    const qc = useQueryClient();
    return useMutation({
        mutationFn: updateHotelApi,
        onSuccess: (_, {id}) => {
            qc.invalidateQueries({queryKey: queryKeys.hotels.detail(id)});
            qc.invalidateQueries({queryKey: ["hotels"]});
        },
    });
}

export function useDeleteHotel() {
    const qc = useQueryClient();
    return useMutation({
        mutationFn: deleteHotelApi,
        onSuccess: () => qc.invalidateQueries({queryKey: ["hotels"]}),
    });
}

export function useAddRoom() {
    const qc = useQueryClient();
    return useMutation({
        mutationFn: addRoomApi,
        onSuccess: (_, {hotelId}) =>
            qc.invalidateQueries({queryKey: queryKeys.hotels.rooms(hotelId)}),
    });
}
