package dev.roqeebah.staybooking;

import static org.assertj.core.api.Assertions.assertThat;

import dev.roqeebah.staybooking.booking.BookingService;
import dev.roqeebah.staybooking.ical.IcalService;
import dev.roqeebah.staybooking.search.SearchService;
import dev.roqeebah.staybooking.search.SearchService.Query;
import dev.roqeebah.staybooking.search.SearchService.RoomResult;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SearchAndIcalTest {

    static final LocalDate IN = LocalDate.of(2027, 8, 10), OUT = LocalDate.of(2027, 8, 13);

    @Autowired SearchService search;
    @Autowired IcalService ical;
    @Autowired BookingService bookings;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("delete from bookings");
    }

    List<String> names(Query q) {
        return search.search(q).stream().map(RoomResult::name).toList();
    }

    @Test
    void searchFiltersAndSortsCheapestFirst() {
        assertThat(names(new Query(IN, OUT, 1, null, null, null)))
                .containsExactly("Surf Camp Dorm", "Garden Suite", "Ocean View Double");
        assertThat(names(new Query(IN, OUT, 3, null, null, null))).containsExactly("Surf Camp Dorm", "Garden Suite");
        assertThat(names(new Query(IN, OUT, 1, "ilorin", null, null))).containsExactly("Garden Suite");
        assertThat(names(new Query(IN, OUT, 1, null, 3_000_000, null))).containsExactly("Surf Camp Dorm", "Garden Suite");
        assertThat(names(new Query(IN, OUT, 1, null, null, "breakfast"))).containsExactly("Garden Suite", "Ocean View Double");
    }

    @Test
    void bookedRoomDisappearsButExpiredHoldDoesNot() {
        var hold = bookings.hold(3L, "Ada", IN, OUT); // Garden Suite
        assertThat(names(new Query(IN, OUT, 1, "Ilorin", null, null))).isEmpty();
        jdbc.update("update bookings set expires_at = now() - interval '1 minute' where id = ?", hold.getId());
        assertThat(names(new Query(IN, OUT, 1, "Ilorin", null, null))).containsExactly("Garden Suite");
    }

    static final String FEED = String.join("\r\n",
            "BEGIN:VCALENDAR", "VERSION:2.0",
            "BEGIN:VEVENT", "UID:abc-123@other-site", "DTSTART;VALUE=DATE:20270901", "DTEND;VALUE=DATE:20270905",
            "SUMMARY:Reserved on another", " site", // folded line
            "END:VEVENT",
            "END:VCALENDAR", "");

    @Test
    void importBlocksDatesAndIsIdempotent() {
        var first = ical.importCalendar(2L, FEED);
        assertThat(first.imported()).isEqualTo(1);
        var again = ical.importCalendar(2L, FEED);
        assertThat(again.imported()).isZero();
        assertThat(again.alreadyKnown()).isEqualTo(1);
        assertThat(names(new Query(LocalDate.of(2027, 9, 2), LocalDate.of(2027, 9, 3), 1, null, null, null)))
                .doesNotContain("Surf Camp Dorm");
    }

    @Test
    void importClashIsReportedNotForced() {
        bookings.hold(2L, "Ada", LocalDate.of(2027, 9, 3), LocalDate.of(2027, 9, 6));
        var result = ical.importCalendar(2L, FEED);
        assertThat(result.imported()).isZero();
        assertThat(result.conflicts()).containsExactly("abc-123@other-site");
    }

    @Test
    void exportShowsDatesNotGuestsAndRoundTrips() {
        bookings.hold(1L, "Ada Lovelace", IN, OUT);
        String ics = ical.export(1L);
        assertThat(ics).contains("DTSTART;VALUE=DATE:20270810", "DTEND;VALUE=DATE:20270813", "SUMMARY:Booked");
        assertThat(ics).doesNotContain("Ada");
        // Feeding our own export to another room blocks the same dates there.
        assertThat(ical.importCalendar(3L, ics).imported()).isEqualTo(1);
    }
}
