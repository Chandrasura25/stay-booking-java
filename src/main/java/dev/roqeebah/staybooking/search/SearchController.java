package dev.roqeebah.staybooking.search;

import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SearchController {

    private final SearchService search;

    public SearchController(SearchService search) {
        this.search = search;
    }

    @GetMapping("/search")
    public List<SearchService.RoomResult> search(@RequestParam LocalDate checkIn,
                                                 @RequestParam LocalDate checkOut,
                                                 @RequestParam(defaultValue = "1") int guests,
                                                 @RequestParam(required = false) String city,
                                                 @RequestParam(required = false) Integer maxPriceCents,
                                                 @RequestParam(required = false) String amenity) {
        return search.search(new SearchService.Query(checkIn, checkOut, guests, city, maxPriceCents, amenity));
    }
}
