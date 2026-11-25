package dev.roqeebah.staybooking.payment;

import static org.assertj.core.api.Assertions.assertThat;

import dev.roqeebah.staybooking.TestcontainersConfiguration;
import dev.roqeebah.staybooking.booking.Booking;
import dev.roqeebah.staybooking.booking.BookingRepository;
import dev.roqeebah.staybooking.booking.BookingService;
import dev.roqeebah.staybooking.payment.PaymentWebhookService.Result;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PaymentWebhookServiceTest {

    @Autowired PaymentWebhookService webhooks;
    @Autowired BookingService bookingService;
    @Autowired BookingRepository bookings;
    @Autowired JdbcTemplate jdbc;

    Booking held;

    @BeforeEach
    void setUp() {
        jdbc.update("delete from bookings");
        jdbc.update("delete from webhook_events");
        // Room 1 is 45,000.00 a night; 3 nights.
        held = bookingService.hold(1L, "Ada", LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 4));
    }

    String event(String id, long bookingId, int amount) {
        return "{\"id\":\"" + id + "\",\"type\":\"payment.succeeded\",\"bookingId\":" + bookingId + ",\"amountCents\":" + amount + "}";
    }

    Result send(String body) {
        return webhooks.handle(webhooks.signatureFor(body, Instant.now().getEpochSecond()), body);
    }

    Booking.Status status() {
        return bookings.findById(held.getId()).orElseThrow().getStatus();
    }

    @Test
    void validPaymentConfirmsTheHold() {
        assertThat(send(event("evt_1", held.getId(), held.getTotalCents()))).isEqualTo(Result.CONFIRMED);
        assertThat(status()).isEqualTo(Booking.Status.CONFIRMED);
    }

    @Test
    void duplicateDeliveryChangesNothing() {
        String body = event("evt_1", held.getId(), held.getTotalCents());
        assertThat(send(body)).isEqualTo(Result.CONFIRMED);
        assertThat(send(body)).isEqualTo(Result.DUPLICATE);
        assertThat(status()).isEqualTo(Booking.Status.CONFIRMED);
        assertThat(jdbc.queryForObject("select count(*) from webhook_events", Integer.class)).isEqualTo(1);
    }

    @Test
    void tamperedBodyIsRejected() {
        String body = event("evt_1", held.getId(), held.getTotalCents());
        String header = webhooks.signatureFor(body, Instant.now().getEpochSecond());
        String tampered = event("evt_1", held.getId(), 1);
        assertThat(webhooks.handle(header, tampered)).isEqualTo(Result.BAD_SIGNATURE);
        assertThat(webhooks.handle("t=1,v1=zz", body)).isEqualTo(Result.BAD_SIGNATURE);
        assertThat(webhooks.handle(null, body)).isEqualTo(Result.BAD_SIGNATURE);
        assertThat(status()).isEqualTo(Booking.Status.HELD);
    }

    @Test
    void staleSignatureIsRejected() {
        String body = event("evt_1", held.getId(), held.getTotalCents());
        long tenMinutesAgo = Instant.now().minusSeconds(600).getEpochSecond();
        assertThat(webhooks.handle(webhooks.signatureFor(body, tenMinutesAgo), body)).isEqualTo(Result.BAD_SIGNATURE);
    }

    @Test
    void wrongAmountIsFlaggedNotConfirmed() {
        assertThat(send(event("evt_1", held.getId(), held.getTotalCents() - 100))).isEqualTo(Result.NEEDS_REFUND);
        assertThat(status()).isEqualTo(Booking.Status.NEEDS_REFUND);
    }

    @Test
    void paymentAfterHoldExpiredIsFlaggedAndNeverRebooked() {
        jdbc.update("update bookings set expires_at = now() - interval '1 minute' where id = ?", held.getId());
        assertThat(send(event("evt_1", held.getId(), held.getTotalCents()))).isEqualTo(Result.NEEDS_REFUND);
        assertThat(status()).isEqualTo(Booking.Status.NEEDS_REFUND);
    }
}
