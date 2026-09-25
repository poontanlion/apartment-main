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
 * Application-level integration tests.
 *
 * These tests start the complete Spring Boot application context
 * and verify that the main application infrastructure is correctly
 * configured and connected.
 *
 * API-specific success/failure tests are handled separately in
 * ControllerTest classes such as RoomControllerTest and
 * BookingControllerTest.
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
     * TEST: Application Context
     *
     * What this test does:
     * - Starts the complete Spring Boot application.
     * - Loads configuration, controllers, services and repositories.
     * - Uses the H2 in-memory database configured for testing.
     *
     * Expected result:
     * - The test passes if Spring can start without
     *   configuration or dependency-injection errors.
     *
     * This test does NOT test a specific API endpoint.
     */
    @Test
    void contextLoads() {
        // If Spring cannot start, this test automatically fails.
    }

    /**
     * TEST: Repository Dependency Injection
     *
     * What this test does:
     * - Checks that every repository required by the application
     *   has been successfully created by Spring.
     *
     * Expected result:
     * - Every repository object must NOT be null.
     *
     * This verifies the application infrastructure rather than
     * testing individual API business logic.
     */
    @Test
    void allRepositoriesAreWired() {

        // User repository
        assertThat(appUserRepository)
                .as("AppUserRepository should be injected")
                .isNotNull();

        // Tenant repository
        assertThat(tenantRepository)
                .as("TenantRepository should be injected")
                .isNotNull();

        // Building repository
        assertThat(buildingRepository)
                .as("BuildingRepository should be injected")
                .isNotNull();

        // Room repository
        assertThat(roomRepository)
                .as("RoomRepository should be injected")
                .isNotNull();

        // Lease repository
        assertThat(leaseRepository)
                .as("LeaseRepository should be injected")
                .isNotNull();

        // Booking repository
        assertThat(bookingRepository)
                .as("BookingRepository should be injected")
                .isNotNull();

        // Utility bill repository
        assertThat(utilityBillRepository)
                .as("UtilityBillRepository should be injected")
                .isNotNull();

        // Maintenance task repository
        assertThat(maintenanceTaskRepository)
                .as("MaintenanceTaskRepository should be injected")
                .isNotNull();

        // Maintenance log repository
        assertThat(maintenanceLogRepository)
                .as("MaintenanceLogRepository should be injected")
                .isNotNull();

        // Supply item repository
        assertThat(supplyItemRepository)
                .as("SupplyItemRepository should be injected")
                .isNotNull();

        // Scheduled reminder repository
        assertThat(scheduledReminderRepository)
                .as("ScheduledReminderRepository should be injected")
                .isNotNull();

        // Application notification repository
        assertThat(appNotificationRepository)
                .as("AppNotificationRepository should be injected")
                .isNotNull();

        // Activity log repository
        assertThat(activityLogRepository)
                .as("ActivityLogRepository should be injected")
                .isNotNull();
    }
}