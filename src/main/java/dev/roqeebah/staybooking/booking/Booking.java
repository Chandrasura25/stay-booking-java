package dev.roqeebah.staybooking.booking;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "bookings")
public class Booking {

    public enum Status { HELD, CONFIRMED, EXPIRED, CANCELLED, NEEDS_REFUND, BLOCKED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "guest_name", nullable = false)
    private String guestName;

    @Column(name = "check_in", nullable = false)
    private LocalDate checkIn;

    @Column(name = "check_out", nullable = false)
    private LocalDate checkOut;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "total_cents")
    private Integer totalCents;

    @Column(nullable = false)
    private String source = "DIRECT";

    @Column(name = "external_uid")
    private String externalUid;

    protected Booking() {}

    public static Booking hold(Long roomId, String guestName, LocalDate checkIn, LocalDate checkOut,
                               int totalCents, Instant expiresAt) {
        Booking b = new Booking();
        b.roomId = roomId;
        b.guestName = guestName;
        b.checkIn = checkIn;
        b.checkOut = checkOut;
        b.status = Status.HELD;
        b.totalCents = totalCents;
        b.expiresAt = expiresAt;
        return b;
    }

    /** Dates taken by another calendar (imported iCal event). */
    public static Booking block(Long roomId, String uid, LocalDate checkIn, LocalDate checkOut) {
        Booking b = new Booking();
        b.roomId = roomId;
        b.guestName = "External calendar";
        b.checkIn = checkIn;
        b.checkOut = checkOut;
        b.status = Status.BLOCKED;
        b.source = "ICAL";
        b.externalUid = uid;
        return b;
    }

    public boolean isActiveHold(Instant now) {
        return status == Status.HELD && expiresAt != null && expiresAt.isAfter(now);
    }

    public void confirm() { this.status = Status.CONFIRMED; this.expiresAt = null; }
    public void flagForRefund() { this.status = Status.NEEDS_REFUND; }

    public Long getId() { return id; }
    public Long getRoomId() { return roomId; }
    public String getGuestName() { return guestName; }
    public LocalDate getCheckIn() { return checkIn; }
    public LocalDate getCheckOut() { return checkOut; }
    public Status getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
    public Integer getTotalCents() { return totalCents; }
    public String getSource() { return source; }
    public String getExternalUid() { return externalUid; }
}
