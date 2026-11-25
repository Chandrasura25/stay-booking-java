package dev.roqeebah.staybooking.ical;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class IcalController {

    private final IcalService ical;

    public IcalController(IcalService ical) {
        this.ical = ical;
    }

    @GetMapping(path = "/rooms/{roomId}/calendar.ics", produces = "text/calendar")
    public ResponseEntity<String> export(@PathVariable Long roomId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=room-" + roomId + ".ics")
                .body(ical.export(roomId));
    }

    @PostMapping(path = "/rooms/{roomId}/calendar/import", consumes = {"text/calendar", "text/plain"})
    public IcalService.ImportResult importCalendar(@PathVariable Long roomId, @RequestBody String ics) {
        return ical.importCalendar(roomId, ics);
    }
}
