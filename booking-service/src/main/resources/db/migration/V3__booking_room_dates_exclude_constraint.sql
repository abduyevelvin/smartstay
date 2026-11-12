-- btree_gist lets VARCHAR be used in a GIST index alongside range types.
-- Without it the EXCLUDE on (room_id WITH =, daterange … WITH &&) would fail
-- because VARCHAR is a btree-only type.
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Hard database guarantee: no two non-cancelled bookings for the same room
-- may have overlapping date ranges.
-- daterange(check_in, check_out, '[)') = closed on check_in, open on check_out,
-- which matches the half-open room-night convention (check-in day inclusive,
-- check-out day exclusive).
-- The partial-index predicate (status <> 'CANCELLED') mirrors the application
-- logic: cancelled bookings release the slot.
ALTER TABLE bookings
    ADD CONSTRAINT excl_room_dates_no_overlap
        EXCLUDE USING gist (
            room_id WITH =,
            daterange(check_in, check_out, '[)') WITH &&
            ) WHERE (status <> 'CANCELLED');
