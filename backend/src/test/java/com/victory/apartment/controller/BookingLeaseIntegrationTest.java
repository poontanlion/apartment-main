package com.victory.apartment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.victory.apartment.model.Booking;
import com.victory.apartment.model.Lease;
import com.victory.apartment.model.Room;
import com.victory.apartment.model.Tenant;
import com.victory.apartment.repository.BookingRepository;
import com.victory.apartment.repository.LeaseRepository;
import com.victory.apartment.repository.RoomRepository;
import com.victory.apartment.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:booking-lease-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class BookingLeaseIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired BookingRepository bookings;
    @Autowired TenantRepository tenants;
    @Autowired LeaseRepository leases;
    @Autowired RoomRepository rooms;

    private Booking booking;
    private Room room;
    private String email;
    private MockHttpSession session;

    @BeforeEach
    void setUp() throws Exception {
        String suffix = UUID.randomUUID().toString();
        email = suffix + "@example.com";
        MvcResult registration = mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("fullname", "Resident", "email", email, "password", "test-password"))))
                .andExpect(status().isOk()).andReturn();
        session = (MockHttpSession) registration.getRequest().getSession(false);
        room = new Room();
        room.setId("room-" + suffix);
        room.setRoomNumber("TEST-" + suffix);
        room.setFloor(1);
        room.setRoomName("Lease test room");
        room.setPrice(6000.0);
        room.setStatus("Reserved");
        rooms.save(room);
        booking = new Booking();
        booking.setId("booking-" + suffix);
        booking.setBookingNo("REF-" + suffix);
        booking.setRoomId(room.getId());
        booking.setRoomNumber(room.getRoomNumber());
        booking.setGuestEmail(email);
        booking.setGuestName("Resident");
        booking.setGuestPhone("0812345678");
        booking.setStatus("Approved");
        booking.setCheckIn("2026-12-01");
        booking.setCheckOut("2027-12-01");
        booking.setTotalPrice(5500.0);
        bookings.save(booking);
    }

    private String application() throws Exception {
        return json.writeValueAsString(Map.of("fullname", "Lease Resident", "phone", "0899999999", "email", email,
                "idCardPassport", "P123456", "emergencyContact", "Family 0811111111"));
    }

    private MvcResult submit() throws Exception {
        return mvc.perform(post("/api/bookings/" + booking.getId() + "/lease").session(session)
                .contentType(MediaType.APPLICATION_JSON).content(application())).andReturn();
    }

    @Test
    void approvedBookingCreatesLinkedTenantLeaseAndOccupiedRoom() throws Exception {
        mvc.perform(post("/api/bookings/" + booking.getId() + "/lease").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(application()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("Completed"))
                .andExpect(jsonPath("$.tenantId").isNotEmpty()).andExpect(jsonPath("$.leaseId").isNotEmpty());
        Booking saved = bookings.findById(booking.getId()).orElseThrow();
        Tenant tenant = tenants.findById(saved.getTenantId()).orElseThrow();
        Lease lease = leases.findById(saved.getLeaseId()).orElseThrow();
        Room occupied = rooms.findById(room.getId()).orElseThrow();
        assertThat(tenant.getFullname()).isEqualTo("Lease Resident");
        assertThat(tenant.getPhone()).isEqualTo("0899999999");
        assertThat(tenant.getEmail()).isEqualTo(email);
        assertThat(tenant.getIdCardPassport()).isEqualTo("P123456");
        assertThat(tenant.getEmergencyContact()).isEqualTo("Family 0811111111");
        assertThat(tenant.getUnitId()).isEqualTo(room.getId());
        assertThat(tenant.getUnitNumber()).isEqualTo(room.getRoomNumber());
        assertThat(lease.getTenantId()).isEqualTo(tenant.getId());
        assertThat(lease.getRoomId()).isEqualTo(room.getId());
        assertThat(lease.getCheckInDate()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(lease.getCheckOutDate()).isEqualTo(LocalDate.of(2027, 12, 1));
        assertThat(lease.getRentAmount()).isEqualTo(5500.0);
        assertThat(lease.getDepositAmount()).isEqualTo(11000.0);
        assertThat(lease.getStatus()).isEqualTo("Active");
        assertThat(occupied.getStatus()).isEqualTo("Occupied");
        assertThat(occupied.getCurrentTenantId()).isEqualTo(tenant.getId());
        mvc.perform(get("/api/tenants")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + tenant.getId() + "')].fullname").value("Lease Resident"));
    }

    @Test
    void retryReturnsExistingLeaseWithoutDuplicates() throws Exception {
        assertThat(submit().getResponse().getStatus()).isEqualTo(200);
        Booking first = bookings.findById(booking.getId()).orElseThrow();
        long tenantCount = tenants.count();
        long leaseCount = leases.count();
        assertThat(submit().getResponse().getStatus()).isEqualTo(200);
        Booking second = bookings.findById(booking.getId()).orElseThrow();
        assertThat(second.getTenantId()).isEqualTo(first.getTenantId());
        assertThat(second.getLeaseId()).isEqualTo(first.getLeaseId());
        assertThat(tenants.count()).isEqualTo(tenantCount);
        assertThat(leases.count()).isEqualTo(leaseCount);
    }

    @Test
    void requiresAuthenticatedOwner() throws Exception {
        mvc.perform(post("/api/bookings/" + booking.getId() + "/lease")
                        .contentType(MediaType.APPLICATION_JSON).content(application()))
                .andExpect(status().isUnauthorized());
        session.setAttribute("userEmail", "someone-else@example.com");
        assertThat(submit().getResponse().getStatus()).isEqualTo(403);
        assertThat(bookings.findById(booking.getId()).orElseThrow().getLeaseId()).isNull();
    }

    @Test
    void simultaneousSubmissionsCreateOnlyOneTenantAndLease() throws Exception {
        long tenantCount = tenants.count();
        long leaseCount = leases.count();
        var first = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try { return submit().getResponse().getStatus(); }
            catch (Exception ex) { throw new RuntimeException(ex); }
        });
        var second = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try { return submit().getResponse().getStatus(); }
            catch (Exception ex) { throw new RuntimeException(ex); }
        });
        assertThat(first.get(10, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(200);
        assertThat(second.get(10, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(200);
        assertThat(tenants.count()).isEqualTo(tenantCount + 1);
        assertThat(leases.count()).isEqualTo(leaseCount + 1);
    }

    @Test
    void pendingRejectedCancelledAndCompletedBookingsCannotCreateLease() throws Exception {
        for (String status : new String[]{"Pending", "Rejected", "Cancelled", "Completed"}) {
            booking.setStatus(status);
            bookings.save(booking);
            assertThat(submit().getResponse().getStatus()).isEqualTo(409);
        }
        assertThat(tenants.findFirstByEmailIgnoreCase(email)).isEmpty();
    }

    @Test
    void rejectsBlankRequiredFieldsAndEmailBelongingToAnotherTenant() throws Exception {
        for (Map<String, String> data : java.util.List.of(
                Map.of("fullname", "   ", "phone", "0899999999"),
                Map.of("fullname", "Resident", "phone", "   "),
                Map.of("fullname", "Resident", "phone", "0899999999", "email", "someone-else@example.com"))) {
            mvc.perform(post("/api/bookings/" + booking.getId() + "/lease").session(session)
                            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(data)))
                    .andExpect(status().isBadRequest());
        }
        assertThat(tenants.findFirstByEmailIgnoreCase(email)).isEmpty();
    }

    @Test
    void missingFieldsResponseIdentifiesEveryRequiredField() throws Exception {
        mvc.perform(post("/api/bookings/" + booking.getId() + "/lease").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fullname\":\" \",\"phone\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.fullname").value("REQUIRED"))
                .andExpect(jsonPath("$.fieldErrors.phone").value("REQUIRED"));
        assertThat(tenants.findFirstByEmailIgnoreCase(email)).isEmpty();
    }

    @Test
    void overlongDetailsReturnFieldErrorsInsteadOfDatabaseFailure() throws Exception {
        mvc.perform(post("/api/bookings/" + booking.getId() + "/lease").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                                "fullname", "Resident", "phone", "0812345678", "emergencyContact", "a".repeat(256)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.emergencyContact").value("TOO_LONG"));
    }

    @Test
    void malformedRequestReturnsAnActionableCode() throws Exception {
        mvc.perform(post("/api/bookings/" + booking.getId() + "/lease").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void rejectsInvalidLeaseDatesAndUnavailableRooms() throws Exception {
        booking.setCheckOut(booking.getCheckIn());
        bookings.save(booking);
        assertThat(submit().getResponse().getStatus()).isEqualTo(400);
        booking.setCheckOut("invalid-date");
        bookings.save(booking);
        assertThat(submit().getResponse().getStatus()).isEqualTo(400);
        booking.setCheckOut("2027-12-01");
        bookings.save(booking);
        for (String status : new String[]{"Occupied", "Maintenance"}) {
            room.setStatus(status);
            rooms.save(room);
            MvcResult result = submit();
            assertThat(result.getResponse().getStatus()).isEqualTo(409);
            assertThat(json.readTree(result.getResponse().getContentAsString()).get("code").asText())
                    .isEqualTo("Occupied".equals(status) ? "ROOM_OCCUPIED" : "ROOM_MAINTENANCE");
        }
        assertThat(tenants.findFirstByEmailIgnoreCase(email)).isEmpty();
    }

    @Test
    void overlappingLeaseRollsBackTenantAndBookingChanges() throws Exception {
        Tenant existing = new Tenant();
        existing.setId("existing-" + UUID.randomUUID());
        existing.setFullname("Original Name");
        existing.setEmail(email);
        tenants.save(existing);
        Lease conflicting = new Lease();
        conflicting.setId("conflict-" + UUID.randomUUID());
        conflicting.setTenantId("another-tenant");
        conflicting.setRoomId(room.getId());
        conflicting.setCheckInDate(LocalDate.of(2026, 12, 1));
        conflicting.setCheckOutDate(LocalDate.of(2027, 12, 1));
        conflicting.setStatus("Active");
        leases.save(conflicting);
        long count = leases.count();
        assertThat(submit().getResponse().getStatus()).isEqualTo(409);
        assertThat(tenants.findById(existing.getId()).orElseThrow().getFullname()).isEqualTo("Original Name");
        assertThat(tenants.findById(existing.getId()).orElseThrow().getUnitId()).isNull();
        assertThat(bookings.findById(booking.getId()).orElseThrow().getLeaseId()).isNull();
        assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo("Approved");
        assertThat(rooms.findById(room.getId()).orElseThrow().getStatus()).isEqualTo("Reserved");
        assertThat(leases.count()).isEqualTo(count);
    }

    @Test
    void optionalFieldsCanBeOmittedAndExistingTenantIsReused() throws Exception {
        Tenant existing = new Tenant();
        existing.setId("existing-" + UUID.randomUUID());
        existing.setFullname("Original Name");
        existing.setEmail(email);
        tenants.save(existing);
        mvc.perform(post("/api/bookings/" + booking.getId() + "/lease").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("fullname", "Updated Name", "phone", "0812345678"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tenantId").value(existing.getId()));
        Tenant saved = tenants.findById(existing.getId()).orElseThrow();
        assertThat(saved.getEmail()).isEqualTo(email);
        assertThat(saved.getFullname()).isEqualTo("Updated Name");
    }

    @Test
    void completedLeaseCannotBeCancelledOrReapprovedThroughBookings() throws Exception {
        assertThat(submit().getResponse().getStatus()).isEqualTo(200);
        mvc.perform(put("/api/bookings/" + booking.getId() + "/cancel")).andExpect(status().isConflict());
        mvc.perform(put("/api/bookings/" + booking.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"Approved\"}"))
                .andExpect(status().isConflict());
        assertThat(rooms.findById(room.getId()).orElseThrow().getStatus()).isEqualTo("Occupied");
    }

    @Test
    void loginEstablishesOwnerSessionAndLogoutInvalidatesIt() throws Exception {
        MvcResult login = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", "test-password"))))
                .andExpect(status().isOk()).andReturn();
        session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session.getAttribute("userEmail")).isEqualTo(email);
        assertThat(submit().getResponse().getStatus()).isEqualTo(200);
        mvc.perform(post("/api/auth/logout").session(session)).andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
    }
}
