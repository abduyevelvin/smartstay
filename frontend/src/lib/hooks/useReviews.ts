"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { queryKeys } from "./queryKeys";
import type { CreateReviewRequest, Page, ReviewResponse } from "@/types";

// ── API calls ──────────────────────────────────────────────────────────────
const fetchHotelReviews = (hotelId: string, page = 0) =>
    api
        .get<Page<ReviewResponse>>(`/api/v1/reviews/hotel/${hotelId}`, {params: {page}})
        .then((r) => r.data);

const fetchMyReviews = () =>
    api.get<ReviewResponse[]>("/api/v1/reviews/my").then((r) => r.data);

const createReviewApi = (data: CreateReviewRequest) =>
    api.post<ReviewResponse>("/api/v1/reviews", data).then((r) => r.data);

const deleteReviewApi = (id: string) => api.delete(`/api/v1/reviews/${id}`);

// ── Query hooks ────────────────────────────────────────────────────────────
export function useHotelReviews(hotelId: string, page = 0) {
    return useQuery({
        queryKey: queryKeys.reviews.hotel(hotelId, page),
        queryFn: () => fetchHotelReviews(hotelId, page),
        enabled: !!hotelId,
    });
}

export function useMyReviews() {
    return useQuery({
        queryKey: queryKeys.reviews.mine,
        queryFn: fetchMyReviews,
    });
}

// ── Mutation hooks ─────────────────────────────────────────────────────────
export function useCreateReview() {
    const qc = useQueryClient();
    return useMutation({
        mutationFn: createReviewApi,
        onSuccess: (_, vars) => {
            qc.invalidateQueries({queryKey: queryKeys.reviews.hotel(vars.hotelId, 0)});
            qc.invalidateQueries({queryKey: queryKeys.reviews.mine});
        },
    });
}

export function useDeleteReview() {
    const qc = useQueryClient();
    return useMutation({
        mutationFn: deleteReviewApi,
        onSuccess: () => qc.invalidateQueries({queryKey: ["reviews"]}),
    });
}
