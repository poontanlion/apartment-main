package com.victory.apartment;
 
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.victory.apartment.repository.ActivityLogRepository;
import com.victory.apartment.repository.AppNotificationRepository;
import com.victory.apartment.repository.AppUserRepository;
import com.victory.apartment.repository.BookingRepository;
import com.victory.apartment.repository.BuildingRepository;
import com.victory.apartment.repository.LeaseRepository;
import com.victory.apartment.repository.MaintenanceLogRepository;
import com.victory.apartment.repository.MaintenanceTaskRepository;
import com.victory.apartment.repository.RoomRepository;
import com.victory.apartment.repository.ScheduledReminderRepository;
import com.victory.apartment.repository.SupplyItemRepository;
import com.victory.apartment.repository.TenantRepository;
import com.victory.apartment.repository.UtilityBillRepository;
 
/**
 * Basic application context tests.
 *
 * These tests boot the full Spring application context (using the H2
 * in-memory database configured in application.properties) and verify
 * that everything wires together correctly.
 */
@SpringBootTest
class ApplicationTests {
 
    @Autowired
    private AppUserRepository appUserRepository;
 
    @Autowired
    private TenantRepository tenantRepository;
 
    @Autowired
    private BuildingRepository buildingRepository;
 
    @Autowired
    private RoomRepository roomRepository;
 
    @Autowired
    private LeaseRepository leaseRepository;
 
    @Autowired
    private BookingRepository bookingRepository;
 
    @Autowired
    private UtilityBillRepository utilityBillRepository;
 
    @Autowired
    private MaintenanceTaskRepository maintenanceTaskRepository;
 
    @Autowired
    private MaintenanceLogRepository maintenanceLogRepository;
 
    @Autowired
    private SupplyItemRepository supplyItemRepository;
 
    @Autowired
    private ScheduledReminderRepository scheduledReminderRepository;
 
    @Autowired
    private AppNotificationRepository appNotificationRepository;
 
    @Autowired
    private ActivityLogRepository activityLogRepository;
 
    /**
     * Verifies that the Spring application context starts up without errors.
     */
    @Test
    void contextLoads() {
    }
 
    /**
     * Verifies that all JPA repository beans used across the application
     * are correctly created and injected by the Spring context.
     */
    @Test
    void allRepositoriesAreWired() {
        assertThat(appUserRepository).isNotNull();
        assertThat(tenantRepository).isNotNull();
        assertThat(buildingRepository).isNotNull();
        assertThat(roomRepository).isNotNull();
        assertThat(leaseRepository).isNotNull();
        assertThat(bookingRepository).isNotNull();
        assertThat(utilityBillRepository).isNotNull();
        assertThat(maintenanceTaskRepository).isNotNull();
        assertThat(maintenanceLogRepository).isNotNull();
        assertThat(supplyItemRepository).isNotNull();
        assertThat(scheduledReminderRepository).isNotNull();
        assertThat(appNotificationRepository).isNotNull();
        assertThat(activityLogRepository).isNotNull();
    }
}
 