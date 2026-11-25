package dev.roqeebah.staybooking.ical;

import dev.roqeebah.staybooking.booking.Booking;
import dev.roqeebah.staybooking.booking.BookingRepository;
import dev.roqeebah.staybooking.booking.BookingService;
import dev.roqeebah.staybooking.booking.DatesUnavailableException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Calendar sync with other booking sites. Export shows when a room is taken, never who is staying.
 * Import blocks the dates; it is idempotent by UID, and a clash is reported rather than forced.
 */
@Service
public class IcalService {

    public record ImportResult(int imported, int alreadyKnown, List<String> conflicts) {}

    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private final BookingRepository bookings;
    private final BookingService inserter;
    private final Clock clock;

    public IcalService(BookingRepository bookings, BookingService inserter, Clock clock) {
        this.bookings = bookings;
        this.inserter = inserter;
        this.clock = clock;
    }

    public String export(Long roomId) {
        StringBuilder out = new StringBuilder();
        line(out, "BEGIN:VCALENDAR");
        line(out, "VERSION:2.0");
        line(out, "PRODID:-//stay-booking-java//EN");
        String stamp = STAMP.format(clock.instant());
        for (Booking b : bookings.findByRoomIdOrderByCheckIn(roomId)) {
            boolean taken = b.getStatus() == Booking.Status.CONFIRMED || b.getStatus() == Booking.Status.BLOCKED
                    || b.isActiveHold(clock.instant());
            if (!taken || b.getSource().equals("ICAL")) continue; // don't echo other calendars back to them
            line(out, "BEGIN:VEVENT");
            line(out, "UID:booking-" + b.getId() + "@stay-booking-java");
            line(out, "DTSTAMP:" + stamp);
            line(out, "DTSTART;VALUE=DATE:" + DATE.format(b.getCheckIn()));
            line(out, "DTEND;VALUE=DATE:" + DATE.format(b.getCheckOut()));
            line(out, "SUMMARY:Booked");
            line(out, "END:VEVENT");
        }
        line(out, "END:VCALENDAR");
        return out.toString();
    }

    public ImportResult importCalendar(Long roomId, String ics) {
        int imported = 0, known = 0;
        List<String> conflicts = new ArrayList<>();
        for (Event e : parse(ics)) {
            if (bookings.existsByRoomIdAndExternalUid(roomId, e.uid())) {
                known++;
                continue;
            }
            try {
                inserter.insert(Booking.block(roomId, e.uid(), e.start(), e.end()));
                imported++;
            } catch (DatesUnavailableException clash) {
                conflicts.add(e.uid());
            }
        }
        return new ImportResult(imported, known, conflicts);
    }

    record Event(String uid, LocalDate start, LocalDate end) {}

    static List<Event> parse(String ics) {
        // Unfold: a line starting with a space or tab continues the previous one (RFC 5545 3.1).
        String unfolded = ics.replace("\r\n", "\n").replaceAll("\n[ \t]", "");
        List<Event> events = new ArrayList<>();
        String uid = null;
        LocalDate start = null, end = null;
        boolean inEvent = false;
        for (String raw : unfolded.split("\n")) {
            String l = raw.strip();
            if (l.equals("BEGIN:VEVENT")) { inEvent = true; uid = null; start = end = null; continue; }
            if (l.equals("END:VEVENT")) {
                if (inEvent && uid != null && start != null) {
                    events.add(new Event(uid, start, end != null && end.isAfter(start) ? end : start.plusDays(1)));
                }
                inEvent = false;
                continue;
            }
            if (!inEvent) continue;
            int colon = l.indexOf(':');
            if (colon < 0) continue;
            String name = l.substring(0, colon).split(";")[0].toUpperCase();
            String value = l.substring(colon + 1).trim();
            switch (name) {
                case "UID" -> uid = value;
                case "DTSTART" -> start = date(value);
                case "DTEND" -> end = date(value);
                default -> { }
            }
        }
        return events;
    }

    /** Takes yyyyMMdd from either a DATE or a DATE-TIME value. */
    private static LocalDate date(String value) {
        return value.length() >= 8 ? LocalDate.parse(value.substring(0, 8), DATE) : null;
    }

    private static void line(StringBuilder out, String s) {
        out.append(s).append("\r\n");
    }
}
