package dev.roqeebah.staybooking.search;

import java.sql.Array;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/** Rooms free for the whole stay, filtered, cheapest first. */
@Service
public class SearchService {

    public record Query(LocalDate checkIn, LocalDate checkOut, int guests, String city, Integer maxPriceCents, String amenity) {}

    public record RoomResult(long id, String name, String city, int maxGuests, int priceCents, List<String> amenities) {}

    private final NamedParameterJdbcTemplate jdbc;

    public SearchService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<RoomResult> search(Query q) {
        if (!q.checkOut().isAfter(q.checkIn())) {
            throw new IllegalArgumentException("check-out must be after check-in");
        }
        StringBuilder sql = new StringBuilder("""
                select r.id, r.name, r.city, r.max_guests, r.price_cents, r.amenities
                from rooms r
                where r.max_guests >= :guests
                  and not exists (
                      select 1 from bookings b
                      where b.room_id = r.id
                        and (b.status in ('CONFIRMED', 'BLOCKED') or (b.status = 'HELD' and b.expires_at > now()))
                        and daterange(b.check_in, b.check_out, '[)') && daterange(:checkIn, :checkOut, '[)'))
                """);
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("guests", q.guests())
                .addValue("checkIn", q.checkIn())
                .addValue("checkOut", q.checkOut());
        if (q.city() != null && !q.city().isBlank()) {
            sql.append(" and lower(r.city) = lower(:city)");
            p.addValue("city", q.city());
        }
        if (q.maxPriceCents() != null) {
            sql.append(" and r.price_cents <= :maxPrice");
            p.addValue("maxPrice", q.maxPriceCents());
        }
        if (q.amenity() != null && !q.amenity().isBlank()) {
            sql.append(" and :amenity = any(r.amenities)");
            p.addValue("amenity", q.amenity());
        }
        sql.append(" order by r.price_cents, r.id");

        return jdbc.query(sql.toString(), p, (rs, i) -> {
            Array arr = rs.getArray("amenities");
            List<String> amenities = arr == null ? List.of() : List.of((String[]) arr.getArray());
            return new RoomResult(rs.getLong("id"), rs.getString("name"), rs.getString("city"),
                    rs.getInt("max_guests"), rs.getInt("price_cents"), new ArrayList<>(amenities));
        });
    }
}
