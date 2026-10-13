package com.victory.apartment.service;

import com.victory.apartment.model.Booking;
import com.victory.apartment.model.Lease;
import com.victory.apartment.model.Room;
import com.victory.apartment.repository.BookingRepository;
import com.victory.apartment.repository.LeaseRepository;
import com.victory.apartment.repository.RoomRepository;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class LeaseService {

    private final LeaseRepository leaseRepo;
    private final RoomRepository roomRepo;
    private final ActivityLogService logService;
    private final BookingRepository bookingRepo;

    public LeaseService(LeaseRepository leaseRepo, RoomRepository roomRepo, ActivityLogService logService,
                        BookingRepository bookingRepo) {
        this.leaseRepo = leaseRepo;
        this.roomRepo = roomRepo;
        this.logService = logService;
        this.bookingRepo = bookingRepo;
    }

    public List<Lease> getAll() {
        return leaseRepo.findAllByOrderByCreatedAtDesc();
    }

    /**
     * Check for occupancy conflicts: no two active leases can overlap on the same room.
     */
    public Map<String, Object> checkConflict(String roomId, String startDate, String endDate, String excludeLeaseId) {
        Map<String, Object> result = new HashMap<>();
        List<Lease> activeLeases = leaseRepo.findByRoomIdAndStatus(roomId, "Active");

        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);

        for (Lease lease : activeLeases) {
            if (excludeLeaseId != null && lease.getId().equals(excludeLeaseId)) continue;

            LocalDate leaseStart = lease.getCheckInDate();
            LocalDate leaseEnd = lease.getCheckOutDate();
            if (leaseStart == null || leaseEnd == null) continue;

            // Overlap: start1 < end2 AND start2 < end1
            if (start.isBefore(leaseEnd) && leaseStart.isBefore(end)) {
                result.put("hasConflict", true);
                result.put("conflictingLease", lease);
                return result;
            }
        }

        result.put("hasConflict", false);
        return result;
    }

    /**
     * Approved bookings that have not become a lease yet still hold their dates.
     */
    private Optional<Booking> findApprovedBookingConflict(Lease lease, String excludeBookingId) {
        for (Booking booking : bookingRepo.findByRoomIdAndStatusIn(lease.getRoomId(), List.of("Approved"))) {
            if (booking.getId().equals(excludeBookingId) || booking.getLeaseId() != null) continue;
            if (booking.getCheckIn() == null || booking.getCheckOut() == null) continue;
            LocalDate start;
            LocalDate end;
            try {
                start = LocalDate.parse(booking.getCheckIn());
                end = LocalDate.parse(booking.getCheckOut());
            } catch (DateTimeParseException ex) {
                continue;
            }
            if (lease.getCheckInDate().isBefore(end) && start.isBefore(lease.getCheckOutDate())) {
                return Optional.of(booking);
            }
        }
        return Optional.empty();
    }

    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> saveLease(Lease leaseData) {
        return saveLease(leaseData, null);
    }

    /**
     * @param sourceBookingId the approved booking this lease is created from; it does not block itself.
     */
    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> saveLease(Lease leaseData, String sourceBookingId) {
        Map<String, Object> result = new HashMap<>();

        if (leaseData.getRoomId() == null || leaseData.getCheckInDate() == null || leaseData.getCheckOutDate() == null) {
            result.put("success", false);
            result.put("message", "Room, check-in date, and check-out date are required.");
            return result;
        }

        // Serialize assignments to the same room, including booking-to-lease submissions.
        if (roomRepo.findByIdForUpdate(leaseData.getRoomId()).isEmpty()) {
            result.put("success", false);
            result.put("message", "Room not found.");
            return result;
        }

        // Check conflict
        Map<String, Object> conflict = checkConflict(
            leaseData.getRoomId(),
            leaseData.getCheckInDate().toString(),
            leaseData.getCheckOutDate().toString(),
            leaseData.getId()
        );

        if ((boolean) conflict.get("hasConflict")) {
            Lease conflicting = (Lease) conflict.get("conflictingLease");
            result.put("success", false);
            result.put("message", String.format(
                "ห้อง %s มีการจองแล้วกับผู้เช่า \"%s\" (%s ถึง %s)",
                leaseData.getRoomNumber(), conflicting.getTenantName(),
                conflicting.getCheckInDate(), conflicting.getCheckOutDate()
            ));

            return result;
        }

        Optional<Booking> approvedBooking = findApprovedBookingConflict(leaseData, sourceBookingId);
        if (approvedBooking.isPresent()) {
            Booking booking = approvedBooking.get();
            result.put("success", false);
            result.put("message", String.format(
                "ห้อง %s มีการจองที่อนุมัติแล้ว %s ของ \"%s\" (%s ถึง %s) กรุณายกเลิกหรือปฏิเสธการจองนั้นก่อน",
                leaseData.getRoomNumber(), booking.getBookingNo(), booking.getGuestName(),
                booking.getCheckIn(), booking.getCheckOut()
            ));
            return result;
        }

        if (leaseData.getId() == null || leaseData.getId().isEmpty()) {
            leaseData.setId("ls-" + UUID.randomUUID().toString().substring(0, 8));
            leaseData.setCreatedAt(LocalDateTime.now());
            if (leaseData.getStatus() == null) leaseData.setStatus("Active");
        }

        Lease saved = leaseRepo.save(leaseData);

        // Update room status
        roomRepo.findById(saved.getRoomId()).ifPresent(room -> {
            room.setStatus("Occupied");
            room.setCurrentTenantId(saved.getTenantId());
            room.setCurrentTenantName(saved.getTenantName());
            room.setPrice(saved.getRentAmount());
            roomRepo.save(room);
        });

        logService.log("Lease Created/Updated",
            String.format("Assigned tenant %s to Unit %s (%s to %s)",
                saved.getTenantName(), saved.getRoomNumber(),
                saved.getCheckInDate(), saved.getCheckOutDate()));

        result.put("success", true);
        result.put("lease", saved);
        return result;
    }

    @org.springframework.transaction.annotation.Transactional
    public void terminate(String leaseId) {
        leaseRepo.findById(leaseId).ifPresent(lease -> {
            lease.setStatus("Terminated");
            leaseRepo.save(lease);

            // Hand the room to another active lease covering today; future-only leases leave it Available
            // (bookings are checked against those leases instead).
            roomRepo.findByIdForUpdate(lease.getRoomId()).ifPresent(room -> {
                LocalDate today = LocalDate.now();
                Optional<Lease> current = leaseRepo.findByRoomIdAndStatus(room.getId(), "Active").stream()
                    .filter(other -> !other.getId().equals(lease.getId()))
                    .filter(other -> other.getCheckInDate() != null && other.getCheckOutDate() != null
                        && !today.isBefore(other.getCheckInDate()) && !today.isAfter(other.getCheckOutDate()))
                    .findFirst();
                if (current.isPresent()) {
                    room.setStatus("Occupied");
                    room.setCurrentTenantId(current.get().getTenantId());
                    room.setCurrentTenantName(current.get().getTenantName());
                } else {
                    room.setStatus("Available");
                    room.setCurrentTenantId(null);
                    room.setCurrentTenantName(null);
                }
                roomRepo.save(room);
            });

            logService.log("Lease Terminated",
                String.format("Terminated lease for Unit %s (%s)", lease.getRoomNumber(), lease.getTenantName()));
        });
    }
}
