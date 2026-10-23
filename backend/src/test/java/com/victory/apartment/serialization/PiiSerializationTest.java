package com.victory.apartment.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.victory.apartment.model.AppUser;
import com.victory.apartment.model.Booking;
import com.victory.apartment.model.MaintenanceTask;
import com.victory.apartment.model.Lease;
import com.victory.apartment.model.Tenant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PiiSerializationTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void appUserSerializationNeverIncludesPassword() throws Exception {
        AppUser user = new AppUser("usr-1", "person@example.com", "secret-password", "Person", "0812345678", "customer");

        String json = mapper.writeValueAsString(user);

        assertFalse(json.contains("secret-password"));
        assertFalse(json.contains("\"password\""));
        assertTrue(json.contains("p***@example.com"));
        assertTrue(json.contains("xxxxxx5678"));
        assertFalse(json.contains("person@example.com"));
        assertFalse(json.contains("0812345678"));
    }

    @Test
    void bookingAndMaintenanceSerializationMaskPhoneAndEmail() throws Exception {
        Booking booking = new Booking();
        booking.setGuestPhone("081-234-5678");
        booking.setGuestEmail("person@example.com");
        MaintenanceTask task = new MaintenanceTask();
        task.setReporterPhone("0891234567");
        task.setReporterEmail("resident@example.org");

        String bookingJson = mapper.writeValueAsString(booking);
        String taskJson = mapper.writeValueAsString(task);

        assertTrue(bookingJson.contains("xxxxxx5678"));
        assertTrue(bookingJson.contains("p***@example.com"));
        assertFalse(bookingJson.contains("081-234-5678"));
        assertFalse(bookingJson.contains("person@example.com"));
        assertTrue(taskJson.contains("reporterPhone\":\"xxxxxx4567"));
        assertTrue(taskJson.contains("r***@example.org"));
        assertFalse(taskJson.contains("0891234567"));
        assertFalse(taskJson.contains("resident@example.org"));
    }

    @Test
    void leaseSerializationMasksTenantPhone() throws Exception {
        Lease lease = new Lease();
        lease.setTenantPhone("0812345678");

        String json = mapper.writeValueAsString(lease);

        assertTrue(json.contains("tenantPhone\":\"xxxxxx5678"));
        assertFalse(json.contains("0812345678"));
    }

    @Test
    void tenantSerializationMasksPhoneAndEmailToo() throws Exception {
        Tenant tenant = new Tenant();
        tenant.setPhone("0812345678");
        tenant.setEmail("tenant@example.com");

        String json = mapper.writeValueAsString(tenant);

        assertTrue(json.contains("xxxxxx5678"));
        assertTrue(json.contains("t***@example.com"));
        assertFalse(json.contains("0812345678"));
        assertFalse(json.contains("tenant@example.com"));
    }

    @Test
    void tenantSerializationMasksIdentityAndEmergencyPhone() throws Exception {
        Tenant tenant = new Tenant();
        tenant.setIdCardPassport("P123456789");
        tenant.setEmergencyContact("Family 081-234-5678 family@example.com");

        String json = mapper.writeValueAsString(tenant);

        assertTrue(json.contains("******6789"));
        assertTrue(json.contains("Family xxxxxx5678 f***@example.com"));
        assertFalse(json.contains("P123456789"));
        assertFalse(json.contains("081-234-5678"));
        assertFalse(json.contains("family@example.com"));
    }
}
