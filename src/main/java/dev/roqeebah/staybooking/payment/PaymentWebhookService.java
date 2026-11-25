package dev.roqeebah.staybooking.payment;

import dev.roqeebah.staybooking.booking.Booking;
import dev.roqeebah.staybooking.booking.BookingRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Payment-provider webhooks, Stripe-style: header {@code t=<unix seconds>,v1=<hex HMAC-SHA256 of "t.body">}.
 * The rule this class exists for: delivering the same event twice changes nothing,
 * and money that arrives for a booking that can't take it is flagged, never silently re-booked.
 */
@Service
public class PaymentWebhookService {

    public record PaymentEvent(String id, String type, Long bookingId, Integer amountCents) {}

    public enum Result { CONFIRMED, DUPLICATE, NEEDS_REFUND, IGNORED, BAD_SIGNATURE }

    static final Duration TOLERANCE = Duration.ofMinutes(5);

    private final BookingRepository bookings;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;
    private final byte[] secret;

    public PaymentWebhookService(BookingRepository bookings, JdbcTemplate jdbc, ObjectMapper json, Clock clock,
                                 @Value("${payments.webhook-secret}") String secret) {
        this.bookings = bookings;
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public Result handle(String signatureHeader, String rawBody) {
        if (!validSignature(signatureHeader, rawBody)) {
            return Result.BAD_SIGNATURE;
        }
        PaymentEvent event = json.readValue(rawBody, PaymentEvent.class);

        // Record the event id first. If it is already there, this is a redelivery: do nothing.
        // Same transaction as the booking update, so a failure below also un-records it.
        int inserted = jdbc.update("insert into webhook_events (event_id) values (?) on conflict do nothing", event.id());
        if (inserted == 0) {
            return Result.DUPLICATE;
        }
        if (!"payment.succeeded".equals(event.type()) || event.bookingId() == null) {
            return Result.IGNORED;
        }

        Booking booking = bookings.findForUpdate(event.bookingId()).orElse(null);
        if (booking == null) {
            return Result.IGNORED;
        }
        boolean rightAmount = booking.getTotalCents() != null && booking.getTotalCents().equals(event.amountCents());
        if (booking.isActiveHold(clock.instant()) && rightAmount) {
            booking.confirm();
            return Result.CONFIRMED;
        }
        if (booking.getStatus() == Booking.Status.CONFIRMED) {
            return Result.IGNORED; // already paid; a second, different event is not our job to settle here
        }
        // Late (hold expired) or wrong amount: keep the money visible for a refund, don't re-book.
        booking.flagForRefund();
        return Result.NEEDS_REFUND;
    }

    boolean validSignature(String header, String body) {
        if (header == null || body == null) return false;
        String t = null, v1 = null;
        for (String part : header.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length != 2) continue;
            if (kv[0].equals("t")) t = kv[1];
            if (kv[0].equals("v1")) v1 = kv[1];
        }
        if (t == null || v1 == null) return false;
        long ts;
        try {
            ts = Long.parseLong(t);
        } catch (NumberFormatException e) {
            return false;
        }
        long age = Math.abs(clock.instant().getEpochSecond() - ts);
        if (age > TOLERANCE.toSeconds()) return false; // replay window
        byte[] expected = sign(t + "." + body);
        byte[] given;
        try {
            given = HexFormat.of().parseHex(v1);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return MessageDigest.isEqual(expected, given); // constant-time compare
    }

    /** Builds a valid header; used by tests and the demo "simulate payment" call. */
    public String signatureFor(String body, long unixSeconds) {
        return "t=" + unixSeconds + ",v1=" + HexFormat.of().formatHex(sign(unixSeconds + "." + body));
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
