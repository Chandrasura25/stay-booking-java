package dev.roqeebah.staybooking.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import dev.roqeebah.staybooking.TestcontainersConfiguration;

/** Runs against a real Postgres in Docker (Testcontainers), not a mock. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BookingServiceTest {

    static final long ROOM = 1L;
    static final LocalDate JUN_1 = LocalDate.of(2027, 6, 1);
    static final LocalDate JUN_4 = LocalDate.of(2027, 6, 4);
    static final LocalDate JUN_6 = LocalDate.of(2027, 6, 6);

    @Autowired BookingService service;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("delete from bookings");
    }

    @Test
    void overlappingStayIsRefused() {
        service.hold(ROOM, "Ada", JUN_1, JUN_4);
        assertThatThrownBy(() -> service.hold(ROOM, "Bola", LocalDate.of(2027, 6, 3), JUN_6))
                .isInstanceOf(DatesUnavailableException.class);
    }

    @Test
    void backToBackStaysAreFine() {
        service.hold(ROOM, "Ada", JUN_1, JUN_4);
        assertThat(service.hold(ROOM, "Bola", JUN_4, JUN_6).getId()).isNotNull();
    }

    @Test
    void ruleHoldsEvenWhenAppIsBypassed() {
        service.hold(ROOM, "Ada", JUN_1, JUN_4);
        assertThatThrownBy(() -> jdbc.update(
                "insert into bookings (room_id, guest_name, check_in, check_out, status) values (?, 'Raw SQL', ?, ?, 'CONFIRMED')",
                ROOM, JUN_1, JUN_4))
                .hasMessageContaining("no_double_booking");
    }

    @Test
    void expiredHoldFreesTheDates() {
        service.hold(ROOM, "Ada", JUN_1, JUN_4);
        jdbc.update("update bookings set expires_at = now() - interval '1 minute'");
        assertThat(service.expireHolds()).isEqualTo(1);
        assertThat(service.hold(ROOM, "Bola", JUN_1, JUN_4).getId()).isNotNull();
    }

    /**
     * Ten guests ask for the same room and dates at the same moment, each on its
     * own database connection. Exactly one wins; the database refuses the rest.
     */
    @Test
    void tenSimultaneousRequestsOneWinner() throws Exception {
        int n = 10;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger won = new AtomicInteger(), refused = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String guest = "Guest " + i;
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    service.hold(ROOM, guest, JUN_1, JUN_4);
                    won.incrementAndGet();
                } catch (DatesUnavailableException e) {
                    refused.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(won.get()).isEqualTo(1);
        assertThat(refused.get()).isEqualTo(n - 1);
        assertThat(jdbc.queryForObject("select count(*) from bookings where status = 'HELD'", Integer.class)).isEqualTo(1);
    }
}
