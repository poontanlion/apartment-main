package com.victory.apartment.service;

import com.victory.apartment.model.Booking;
import com.victory.apartment.model.Lease;
import com.victory.apartment.model.Room;
import com.victory.apartment.model.Tenant;
import com.victory.apartment.repository.BookingRepository;
import com.victory.apartment.repository.RoomRepository;
import com.victory.apartment.repository.TenantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;

@Service
public class BookingLeaseService {
    public record LeaseApplication(String fullname, String phone, String email,
                                   String idCardPassport, String emergencyContact) {}

    public static class LeaseSubmissionException extends ResponseStatusException {
        private final String code;
        private final Map<String, String> fieldErrors;

        public LeaseSubmissionException(HttpStatus status, String code, String message) {
            this(status, code, message, Map.of());
        }

        public LeaseSubmissionException(HttpStatus status, String code, String message, Map<String, String> fieldErrors) {
            super(status, message);
            this.code = code;
            this.fieldErrors = Map.copyOf(fieldErrors);
        }

        public String getCode() { return code; }
        public Map<String, String> getFieldErrors() { return fieldErrors; }
    }

    private final BookingRepository bookings;
    private final RoomRepository rooms;
    private final TenantRepository tenants;
    private final LeaseService leases;

    public BookingLeaseService(BookingRepository bookings, RoomRepository rooms,
                               TenantRepository tenants, LeaseService leases) {
        this.bookings = bookings;
        this.rooms = rooms;
        this.tenants = tenants;
        this.leases = leases;
    }

    @Transactional
    public Booking createLease(String bookingId, String ownerEmail, LeaseApplication application) {
        if (ownerEmail == null || ownerEmail.isBlank()) {
            throw new LeaseSubmissionException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Please sign in again to create your lease agreement.");
        }
        Booking booking = bookings.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new LeaseSubmissionException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "Booking not found."));
        if (!ownerEmail.equalsIgnoreCase(booking.getGuestEmail())) {
            throw new LeaseSubmissionException(HttpStatus.FORBIDDEN, "NOT_BOOKING_OWNER", "You can only create a lease for your own booking.");
        }
        // Retries return the existing result without creating duplicate tenants or leases.
        if (booking.getLeaseId() != null) return booking;
        if (!"Approved".equals(booking.getStatus())) {
            throw new LeaseSubmissionException(HttpStatus.CONFLICT, "BOOKING_NOT_APPROVED", "The booking must be approved before creating a lease.");
        }
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        if (clean(application.fullname()).isEmpty()) fieldErrors.put("fullname", "REQUIRED");
        if (clean(application.phone()).isEmpty()) fieldErrors.put("phone", "REQUIRED");
        Map<String, String> fields = Map.of(
                "fullname", clean(application.fullname()), "phone", clean(application.phone()),
                "email", clean(application.email()), "idCardPassport", clean(application.idCardPassport()),
                "emergencyContact", clean(application.emergencyContact()));
        fields.forEach((field, value) -> {
            if (value.length() > 255) fieldErrors.put(field, "TOO_LONG");
        });
        String email = clean(application.email());
        if (!email.isEmpty() && !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            fieldErrors.put("email", "INVALID_EMAIL");
        } else if (!email.isEmpty() && !ownerEmail.equalsIgnoreCase(email)) {
            fieldErrors.put("email", "EMAIL_MISMATCH");
        }
        if (!fieldErrors.isEmpty()) {
            throw new LeaseSubmissionException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                    "Please correct the highlighted fields.", fieldErrors);
        }
        LocalDate start;
        LocalDate end;
        try {
            start = LocalDate.parse(clean(booking.getCheckIn()));
            end = LocalDate.parse(clean(booking.getCheckOut()));
        } catch (DateTimeParseException ex) {
            throw new LeaseSubmissionException(HttpStatus.BAD_REQUEST, "INVALID_LEASE_DATES", "The booking must have valid lease dates.");
        }
        if (!end.isAfter(start)) {
            throw new LeaseSubmissionException(HttpStatus.BAD_REQUEST, "INVALID_LEASE_PERIOD", "Lease end date must be after the start date.");
        }
        if (booking.getRoomId() == null) {
            throw new LeaseSubmissionException(HttpStatus.BAD_REQUEST, "ROOM_NOT_ASSIGNED", "The booking must have an assigned room.");
        }
        Room room = rooms.findByIdForUpdate(booking.getRoomId())
                .orElseThrow(() -> new LeaseSubmissionException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "Booked room not found."));
        if ("Occupied".equalsIgnoreCase(room.getStatus())) {
            throw new LeaseSubmissionException(HttpStatus.CONFLICT, "ROOM_OCCUPIED", "The booked room is occupied. Please contact management.");
        }
        if ("Maintenance".equalsIgnoreCase(room.getStatus())) {
            throw new LeaseSubmissionException(HttpStatus.CONFLICT, "ROOM_MAINTENANCE", "The booked room is under maintenance. Please contact management.");
        }
        Double rent = booking.getTotalPrice() != null ? booking.getTotalPrice() : room.getPrice();
        if (rent == null || !Double.isFinite(rent) || rent < 0) {
            throw new LeaseSubmissionException(HttpStatus.BAD_REQUEST, "INVALID_RENT", "The booking must have a valid rent amount.");
        }
        // Use the account email to keep the tenant linked to the resident when email is omitted.
        if (email.isEmpty()) email = booking.getGuestEmail();
        Tenant tenant = tenants.findFirstByEmailIgnoreCase(email).orElseGet(Tenant::new);
        if (tenant.getId() == null) {
            tenant.setId("t-" + UUID.randomUUID());
            tenant.setCreatedAt(LocalDateTime.now());
        }
        tenant.setFullname(clean(application.fullname()));
        tenant.setPhone(clean(application.phone()));
        tenant.setEmail(email);
        tenant.setIdCardPassport(clean(application.idCardPassport()));
        tenant.setEmergencyContact(clean(application.emergencyContact()));
        tenant.setUnitId(room.getId());
        tenant.setUnitNumber(room.getRoomNumber());
        tenants.save(tenant);

        Lease lease = new Lease();
        lease.setTenantId(tenant.getId());
        lease.setTenantName(tenant.getFullname());
        lease.setTenantPhone(tenant.getPhone());
        lease.setRoomId(room.getId());
        lease.setRoomNumber(room.getRoomNumber());
        lease.setCheckInDate(start);
        lease.setCheckOutDate(end);
        lease.setRentAmount(rent);
        lease.setBillingCycle("monthly");
        lease.setDepositAmount(rent * 2);
        lease.setNotes("Created from approved booking " + booking.getBookingNo());
        Map<String, Object> result = leases.saveLease(lease);
        if (!Boolean.TRUE.equals(result.get("success"))) {
            // Roll back tenant, lease, room and booking together if assignment fails.
            throw new LeaseSubmissionException(HttpStatus.CONFLICT, "LEASE_CONFLICT", (String) result.get("message"));
        }
        Lease saved = (Lease) result.get("lease");
        booking.setTenantId(tenant.getId());
        booking.setLeaseId(saved.getId());
        booking.setStatus("Completed");
        return bookings.save(booking);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
