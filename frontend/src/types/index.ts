// ─────────────────────────────────────────────────────────────
// Admin
// ─────────────────────────────────────────────────────────────
export type Role = "GUEST" | "HOTEL_MANAGER" | "ADMIN";

export interface UserSummaryResponse {
    id: string;
    email: string;
    firstName: string;
    lastName: string;
    role: Role;
    active: boolean;
    createdAt: string;
}

export interface AssignRoleRequest {
    role: "GUEST" | "HOTEL_MANAGER";
}

// ─────────────────────────────────────────────────────────────
// Auth
// ─────────────────────────────────────────────────────────────
export interface AuthResponse {
    accessToken: string;
    refreshToken: string;
    tokenType: string;
    expiresIn: number;
}

export interface RegisterRequest {
    email: string;
    password: string;
    firstName: string;
    lastName: string;
    role: "GUEST" | "HOTEL_MANAGER";
}

export interface LoginRequest {
    email: string;
    password: string;
}

// ─────────────────────────────────────────────────────────────
// Hotel
// ─────────────────────────────────────────────────────────────
export interface CreateHotelRequest {
    name: string;
    description: string;
    city: string;
    country: string;
    address: string;
    starRating: number;
    amenities: string[];
    checkInTime: string;
    checkOutTime: string;
}

export interface UpdateHotelRequest {
    name?: string;
    description?: string;
    city?: string;
    country?: string;
    address?: string;
    starRating?: number;
    amenities?: string[];
    checkInTime?: string;
    checkOutTime?: string;
}

export interface CreateRoomRequest {
    roomNumber: string;
    type: "SINGLE" | "DOUBLE" | "SUITE" | "PENTHOUSE";
    pricePerNight: number;
    maxOccupancy: number;
    amenities: string[];
    floor: number;
}

export interface HotelResponse {
    id: string;
    name: string;
    description: string;
    city: string;
    country: string;
    address: string;
    starRating: number;
    amenities: string[];
    images: string[];
    managerId: string;
    averageRating: number;
    reviewCount: number;
    checkInTime: string;
    checkOutTime: string;
    policies: Record<string, string>;
}

export interface RoomResponse {
    id: string;
    hotelId: string;
    roomNumber: string;
    type: "SINGLE" | "DOUBLE" | "SUITE" | "PENTHOUSE";
    pricePerNight: number;
    maxOccupancy: number;
    amenities: string[];
    images: string[];
    available: boolean;
    floor: number;
}

export interface HotelSearchParams {
    city?: string;
    country?: string;
    minStars?: number;
    page?: number;
    size?: number;
}

// ─────────────────────────────────────────────────────────────
// Booking
// ─────────────────────────────────────────────────────────────
export type BookingStatus = "PENDING" | "CONFIRMED" | "CANCELLED" | "COMPLETED";

export interface BookingResponse {
    id: string;
    guestId: string;
    guestEmail: string;
    hotelId: string;
    roomId: string;
    checkIn: string;
    checkOut: string;
    totalPrice: number;
    status: BookingStatus;
    specialRequests: string;
    createdAt: string;
}

export interface CreateBookingRequest {
    hotelId: string;
    roomId: string;
    checkIn: string;     // ISO date: "2026-12-01"
    checkOut: string;
    specialRequests?: string;
}

// ─────────────────────────────────────────────────────────────
// Review
// ─────────────────────────────────────────────────────────────
export type Sentiment = "POSITIVE" | "NEUTRAL" | "NEGATIVE";

export interface ReviewResponse {
    id: string;
    hotelId: string;
    guestId: string;
    guestName: string;
    rating: number;
    title: string;
    body: string;
    sentiment: Sentiment | null;
    tags: string[];
    stayDate: string;
    createdAt: string;
    verified: boolean;
}

export interface CreateReviewRequest {
    hotelId: string;
    rating: number;
    title: string;
    body: string;
    stayDate: string;
}

// ─────────────────────────────────────────────────────────────
// AI
// ─────────────────────────────────────────────────────────────
export interface RecommendRequest {
    preferences: string;
}

export interface RecommendResponse {
    recommendation: string;
}

export interface ChatRequest {
    sessionId: string;
    message: string;
}

export interface ChatResponse {
    reply: string;
    sessionId: string;
}

export interface MessageDto {
    role: "USER" | "ASSISTANT";
    content: string;
}

export interface HotelSummaryResponse {
    hotelId: string;
    summary: string;
}

export interface AvailabilityResponse {
    available: boolean;
}

// ─────────────────────────────────────────────────────────────
// Shared
// ─────────────────────────────────────────────────────────────
export interface Page<T> {
    content: T[];
    totalElements: number;
    totalPages: number;
    number: number;
    size: number;
}

export interface ErrorResponse {
    timestamp: string;
    status: number;
    error: string;
    message: string;
    path: string;
}
