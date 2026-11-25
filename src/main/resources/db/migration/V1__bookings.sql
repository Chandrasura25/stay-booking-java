-- btree_gist lets an exclusion constraint mix "=" on room_id with "&&" on a date range.
create extension if not exists btree_gist;

create table rooms (
    id          bigserial primary key,
    name        text    not null,
    city        text    not null,
    max_guests  int     not null check (max_guests > 0),
    price_cents int     not null check (price_cents >= 0)
);

create table bookings (
    id          bigserial primary key,
    room_id     bigint      not null references rooms(id),
    guest_name  text        not null,
    check_in    date        not null,
    check_out   date        not null,
    status      text        not null check (status in ('HELD', 'CONFIRMED', 'EXPIRED', 'CANCELLED')),
    expires_at  timestamptz,
    created_at  timestamptz not null default now(),
    check (check_out > check_in),

    -- The whole point: a room can never have two active stays that overlap.
    -- '[)' is half-open, so checking out on the day the next guest checks in is fine.
    -- Postgres enforces this, so it holds even if application code is bypassed.
    constraint no_double_booking exclude using gist (
        room_id with =,
        daterange(check_in, check_out, '[)') with &&
    ) where (status in ('HELD', 'CONFIRMED'))
);

insert into rooms (name, city, max_guests, price_cents) values
    ('Ocean View Double', 'Lagos', 2, 4500000),
    ('Surf Camp Dorm', 'Lagos', 6, 1500000),
    ('Garden Suite', 'Ilorin', 3, 3000000);
