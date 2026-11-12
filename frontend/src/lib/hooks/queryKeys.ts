export const queryKeys = {
    hotels: {
        list: (params: object) => ["hotels", "list", params] as const,
        detail: (id: string) => ["hotels", id] as const,
        rooms: (hotelId: string) => ["hotels", hotelId, "rooms"] as const,
        availability: (hotelId: string) => ["hotels", hotelId, "availability"] as const,
        manager: (managerId: string) => ["hotels", "manager", managerId] as const,
    },
    bookings: {
        mine: (page: number) => ["bookings", "mine", page] as const,
        hotel: (hotelId: string) => ["bookings", "hotel", hotelId] as const,
        availability: (roomId: string, checkIn: string, checkOut: string) =>
            ["bookings", "availability", roomId, checkIn, checkOut] as const,
    },
    reviews: {
        hotel: (hotelId: string, page: number) => ["reviews", "hotel", hotelId, page] as const,
        mine: ["reviews", "mine"] as const,
    },
    ai: {
        history: (sessionId: string) => ["ai", "chat", sessionId, "history"] as const,
        summary: (hotelId: string) => ["ai", "summary", hotelId] as const,
    },
};
