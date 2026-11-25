package dev.roqeebah.staybooking.booking;

import java.time.LocalDate;

public class DatesUnavailableException extends RuntimeException {
    public DatesUnavailableException(Long roomId, LocalDate checkIn, LocalDate checkOut) {
        super("Room " + roomId + " is not available from " + checkIn + " to " + checkOut);
    }
}
