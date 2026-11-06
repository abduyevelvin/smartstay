-- Booking Service — initial schema

CREATE TABLE bookings
(
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    guest_id         UUID         NOT NULL,
    guest_email      VARCHAR(255) NOT NULL,
    hotel_id         VARCHAR(100) NOT NULL,
    room_id          VARCHAR(100) NOT NULL,
    check_in         DATE         NOT NULL,
    check_out        DATE         NOT NULL,
    total_price      FLOAT8       NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    special_requests TEXT,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_bookings_guest_id ON bookings (guest_id);
CREATE INDEX idx_bookings_hotel_id ON bookings (hotel_id);
CREATE INDEX idx_bookings_room_id ON bookings (room_id);
