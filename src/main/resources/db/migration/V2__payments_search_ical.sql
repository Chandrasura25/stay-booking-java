-- Search filters
alter table rooms add column amenities text[] not null default '{}';
update rooms set amenities = '{wifi,sea-view,breakfast}' where name = 'Ocean View Double';
update rooms set amenities = '{wifi,surfboards}'          where name = 'Surf Camp Dorm';
update rooms set amenities = '{wifi,garden,breakfast}'    where name = 'Garden Suite';

-- Payments and imported calendar blocks
alter table bookings add column total_cents  int;
alter table bookings add column source       text not null default 'DIRECT' check (source in ('DIRECT', 'ICAL'));
alter table bookings add column external_uid text;
create unique index bookings_room_external_uid on bookings (room_id, external_uid) where external_uid is not null;

-- New statuses: NEEDS_REFUND (money arrived but the booking can't take it) and
-- BLOCKED (dates taken by another calendar). BLOCKED must block dates too.
alter table bookings drop constraint bookings_status_check;
alter table bookings add constraint bookings_status_check
    check (status in ('HELD', 'CONFIRMED', 'EXPIRED', 'CANCELLED', 'NEEDS_REFUND', 'BLOCKED'));

alter table bookings drop constraint no_double_booking;
alter table bookings add constraint no_double_booking exclude using gist (
    room_id with =,
    daterange(check_in, check_out, '[)') with &&
) where (status in ('HELD', 'CONFIRMED', 'BLOCKED'));

-- Every webhook event id we have processed. Inserting here first is what makes
-- a duplicate delivery a no-op.
create table webhook_events (
    event_id    text primary key,
    received_at timestamptz not null default now()
);
