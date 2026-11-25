# stay-booking-java

The booking core for a small-stays marketplace (surf camps, boutique hotels, retreats): availability, holds, payments, search and calendar sync. **Java 21 + Spring Boot 4 on PostgreSQL**, tested against a real Postgres in Docker.

It is a Java rewrite of my TypeScript [stay-booking](https://github.com/Chandrasura25/stay-booking), built to answer the same question: **how do you make double-booking impossible rather than unlikely?**

## The idea

A room can't have two active stays that overlap, and the *database* enforces it, not the app:

```sql
constraint no_double_booking exclude using gist (
    room_id with =,
    daterange(check_in, check_out, '[)') with &&
) where (status in ('HELD', 'CONFIRMED', 'BLOCKED'))
```

`'[)'` is half-open, so one guest checking out the day the next arrives is fine, but any real overlap is refused. The service only inserts and translates Postgres's answer (SQLSTATE `23P01`) into a `409 Conflict`. A test inserts an overlapping row with raw SQL to show the rule holds even when the app is bypassed.

## What's here

| Piece | What it does | Tested against real Postgres |
|---|---|---|
| Availability | Exclusion constraint on room + date range (Flyway migrations) | overlap refused, back-to-back allowed, raw-SQL bypass refused, **10 threads on 10 connections → exactly 1 winner** |
| Holds | 15-minute payment window; a scheduled sweep expires holds and frees the dates | expiry frees the dates |
| Payment webhooks | Stripe-style signature `t=…,v1=HMAC-SHA256`, 5-minute replay window, constant-time compare; event ids recorded in the same transaction as the booking change | valid → confirmed, **duplicate delivery changes nothing**, tampered/forged/stale → 401, wrong amount or payment after expiry → `NEEDS_REFUND`, never silently re-booked |
| Search | Rooms free for the whole stay, by guests, city, max price, amenity; cheapest first | each filter; active hold hides a room, expired hold doesn't |
| iCal | Export a room's taken dates (no guest details); import another calendar's events as blocks, idempotent by UID; clashes reported, not forced | import, re-import, clash, folded lines, round trip |

## API

| Method | Path | |
|---|---|---|
| `POST` | `/holds` | `{roomId, guestName, checkIn, checkOut}` → 201, or 409 if taken |
| `GET` | `/rooms/{id}/bookings` | a room's bookings |
| `GET` | `/search?checkIn=&checkOut=&guests=&city=&maxPriceCents=&amenity=` | free rooms, cheapest first |
| `POST` | `/webhooks/payments` | signed payment event (header `Payment-Signature`) |
| `GET` | `/rooms/{id}/calendar.ics` | iCal export |
| `POST` | `/rooms/{id}/calendar/import` | iCal import (`text/calendar`) |

## Run

Needs Java 21 and Docker. Nothing else to install: Maven comes through the wrapper.

```
./mvnw test             # starts Postgres 17 in Docker (Testcontainers) and runs the tests
./mvnw spring-boot:run  # starts Postgres via compose.yaml, then the API on :8080
```

Set `PAYMENTS_WEBHOOK_SECRET` for anything beyond local use.

## Honest limits

- The webhook uses a Stripe-**style** signature with a shared secret. It is not wired to a live Stripe account.
- No authentication or host dashboard. The booking core came first because it is the part that is hardest to get right.
- iCal import parses the common subset (UID, DTSTART, DTEND, folded lines). Recurring events and time zones are not handled.
- A payment for an already-confirmed booking under a new event id is ignored, not refunded automatically.
