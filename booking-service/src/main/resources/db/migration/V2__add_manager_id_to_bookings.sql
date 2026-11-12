-- Add manager_id to bookings so cancel/read ownership checks don't need a Feign call.
-- Nullable — existing rows had no manager context at creation time.

ALTER TABLE bookings
    ADD COLUMN manager_id VARCHAR(255);

CREATE INDEX idx_bookings_manager_id ON bookings (manager_id);
