package dev.roqeebah.staybooking.booking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class BookingController {

    public record HoldRequest(@NotNull Long roomId, @NotBlank String guestName,
                              @NotNull LocalDate checkIn, @NotNull LocalDate checkOut) {}

    private final BookingService service;
    private final BookingRepository bookings;

    public BookingController(BookingService service, BookingRepository bookings) {
        this.service = service;
        this.bookings = bookings;
    }

    @PostMapping("/holds")
    @ResponseStatus(HttpStatus.CREATED)
    public Booking hold(@Valid @RequestBody HoldRequest req) {
        return service.hold(req.roomId(), req.guestName(), req.checkIn(), req.checkOut());
    }

    @GetMapping("/rooms/{roomId}/bookings")
    public List<Booking> bookings(@PathVariable Long roomId) {
        return bookings.findByRoomIdOrderByCheckIn(roomId);
    }

    @ExceptionHandler(DatesUnavailableException.class)
    ResponseEntity<Map<String, String>> unavailable(DatesUnavailableException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
