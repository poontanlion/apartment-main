package com.victory.apartment.repository;

import com.victory.apartment.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, String> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select b from Booking b where b.id = :id")
    java.util.Optional<Booking> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") String id);
    List<Booking> findAllByOrderByCreatedAtDesc();
    List<Booking> findByGuestEmailIgnoreCaseOrderByCreatedAtDesc(String guestEmail);
    List<Booking> findByRoomIdAndStatusIn(String roomId, List<String> statuses);
    List<Booking> findByBookingNoIgnoreCaseOrGuestPhoneOrGuestEmailIgnoreCaseOrderByCreatedAtDesc(String bookingNo, String guestPhone, String guestEmail);
}
