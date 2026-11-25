package dev.roqeebah.staybooking.booking;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByRoomIdOrderByCheckIn(Long roomId);

    boolean existsByRoomIdAndExternalUid(Long roomId, String externalUid);

    /** Row lock so a payment and the expiry sweep can't change the same booking at once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findForUpdate(Long id);

    /** Expired holds stop blocking dates: the constraint only covers HELD, CONFIRMED and BLOCKED. */
    @Modifying
    @Transactional
    @Query("update Booking b set b.status = dev.roqeebah.staybooking.booking.Booking.Status.EXPIRED "
         + "where b.status = dev.roqeebah.staybooking.booking.Booking.Status.HELD and b.expiresAt < :now")
    int expireHolds(Instant now);
}
