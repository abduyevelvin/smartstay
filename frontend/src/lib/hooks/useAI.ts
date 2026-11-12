"use client";

import { useMutation, useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { queryKeys } from "./queryKeys";
import type {
    ChatRequest,
    ChatResponse,
    HotelSummaryResponse,
    MessageDto,
    RecommendRequest,
    RecommendResponse,
} from "@/types";

// ── API calls ──────────────────────────────────────────────────────────────
const recommendApi = (data: RecommendRequest) =>
    api.post<RecommendResponse>("/api/v1/ai/recommend", data).then((r) => r.data);

const chatApi = (data: ChatRequest) =>
    api.post<ChatResponse>("/api/v1/ai/chat", data).then((r) => r.data);

const fetchChatHistory = (sessionId: string) =>
    api.get<MessageDto[]>(`/api/v1/ai/chat/${sessionId}/history`).then((r) => r.data);

const fetchHotelSummary = (hotelId: string) =>
    api.post<HotelSummaryResponse>(`/api/v1/ai/hotels/${hotelId}/summary`).then((r) => r.data);

// ── Mutation hooks ─────────────────────────────────────────────────────────
export function useRecommend() {
    return useMutation({mutationFn: recommendApi});
}

export function useChat() {
    return useMutation({mutationFn: chatApi});
}

// ── Query hooks ────────────────────────────────────────────────────────────
export function useChatHistory(sessionId: string) {
    return useQuery({
        queryKey: queryKeys.ai.history(sessionId),
        queryFn: () => fetchChatHistory(sessionId),
        enabled: !!sessionId,
    });
}

export function useHotelSummary(hotelId: string) {
    return useQuery({
        queryKey: queryKeys.ai.summary(hotelId),
        queryFn: () => fetchHotelSummary(hotelId),
        enabled: !!hotelId,
        staleTime: 1000 * 60 * 10, // summaries don't change often — cache 10 min
    });
}
