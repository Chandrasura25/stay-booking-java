package dev.roqeebah.staybooking.booking;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class BookingService {

    /** Postgres SQLSTATE for an exclusion-constraint violation. */
    static final String EXCLUSION_VIOLATION = "23P01";
    static final Duration HOLD_WINDOW = Duration.ofMinutes(15);

    private final BookingRepository bookings;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public BookingService(BookingRepository bookings, JdbcTemplate jdbc, Clock clock) {
        this.bookings = bookings;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * Holds a room for 15 minutes while the guest pays. The app does not check
     * availability itself: it inserts and lets Postgres's exclusion constraint
     * decide, so two simultaneous requests can never both win.
     */
    public Booking hold(Long roomId, String guestName, LocalDate checkIn, LocalDate checkOut) {
        if (!checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException("check-out must be after check-in");
        }
        int nightly = nightlyPrice(roomId);
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        expireHolds(); // a hold that just lapsed must not block these dates
        return insert(Booking.hold(roomId, guestName, checkIn, checkOut,
                Math.toIntExact(nightly * nights), clock.instant().plus(HOLD_WINDOW)));
    }

    /** Inserts and translates the database's "dates overlap" answer. */
    public Booking insert(Booking booking) {
        try {
            return bookings.saveAndFlush(booking);
        } catch (DataIntegrityViolationException e) {
            if (EXCLUSION_VIOLATION.equals(sqlState(e))) {
                throw new DatesUnavailableException(booking.getRoomId(), booking.getCheckIn(), booking.getCheckOut());
            }
            throw e;
        }
    }

    @Scheduled(fixedDelayString = "PT1M")
    public int expireHolds() {
        return bookings.expireHolds(clock.instant());
    }

    private int nightlyPrice(Long roomId) {
        try {
            return jdbc.queryForObject("select price_cents from rooms where id = ?", Integer.class, roomId);
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("no room " + roomId);
        }
    }

    static String sqlState(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
        }
        return null;
    }
}
