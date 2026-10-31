
package com.victory.apartment.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.victory.apartment.model.Room;
import com.victory.apartment.model.Booking;
import com.victory.apartment.model.ActivityLog;
import com.victory.apartment.model.Lease;
import com.victory.apartment.repository.RoomRepository;
import com.victory.apartment.repository.BookingRepository;
import com.victory.apartment.repository.ActivityLogRepository;
import com.victory.apartment.repository.LeaseRepository;
import com.victory.apartment.repository.TenantRepository;
import com.victory.apartment.service.BookingLeaseService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class PostgresLockIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private LeaseRepository leaseRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private BookingLeaseService bookingLeaseService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // B3.1: ทดสอบ PostgreSQL Row Lock
    @Test
    void secondTransactionWaitsForRoomLock() throws Exception {

        String roomId = UUID.randomUUID().toString();

        Room room = new Room();
        room.setId(roomId);
        room.setRoomNumber("TEST-" + roomId);
        room.setFloor(1);
        room.setStatus("Available");

        roomRepository.saveAndFlush(room);

        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        CountDownLatch secondAttempted = new CountDownLatch(1);

        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);

        try {
            CompletableFuture<Void> first =
                    CompletableFuture.runAsync(() -> {
                        transaction.executeWithoutResult(status -> {
                            roomRepository.findByIdForUpdate(roomId)
                                    .orElseThrow();

                            lockAcquired.countDown();

                            try {
                                if (!releaseLock.await(10, TimeUnit.SECONDS)) {
                                    throw new IllegalStateException(
                                            "Timed out waiting to release lock");
                                }
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException(e);
                            }
                        });
                    });

            assertThat(lockAcquired.await(10, TimeUnit.SECONDS))
                    .isTrue();

            CompletableFuture<Void> second =
                    CompletableFuture.runAsync(() -> {
                        transaction.executeWithoutResult(status -> {
                            secondAttempted.countDown();

                            roomRepository.findByIdForUpdate(roomId)
                                    .orElseThrow();
                        });
                    });

            assertThat(secondAttempted.await(10, TimeUnit.SECONDS))
                    .isTrue();

            Thread.sleep(500);

            assertThat(second.isDone()).isFalse();

            releaseLock.countDown();

            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);

        } finally {
            releaseLock.countDown();
            roomRepository.deleteById(roomId);
        }
    }

    // B3.3: ทดสอบ IgnoreCase Query กับ PostgreSQL
    @Test
    void bookingIgnoreCaseQueriesWorkWithPostgres() {

        String id = UUID.randomUUID().toString();
        String suffix = id.substring(0, 8);

        Booking booking = new Booking();
        booking.setId(id);
        booking.setBookingNo("BK-ABC-" + suffix);
        booking.setGuestEmail("TEST-" + suffix + "@GMAIL.COM");
        booking.setGuestPhone("0812345678");
        booking.setCreatedAt(LocalDateTime.now());
        booking.setStatus("Pending");

        bookingRepository.saveAndFlush(booking);

        try {
            var emailResults =
                    bookingRepository
                            .findByGuestEmailIgnoreCaseOrderByCreatedAtDesc(
                                    "test-" + suffix + "@gmail.com"
                            );

            assertThat(emailResults)
                    .extracting(Booking::getId)
                    .contains(id);

            var bookingNoResults =
                    bookingRepository
                            .findByBookingNoIgnoreCaseOrGuestPhoneOrGuestEmailIgnoreCaseOrderByCreatedAtDesc(
                                    "bk-abc-" + suffix,
                                    "0000000000",
                                    "not-found@example.com"
                            );

            assertThat(bookingNoResults)
                    .extracting(Booking::getId)
                    .contains(id);

        } finally {
            bookingRepository.deleteById(id);
        }
    }

    // B3.4: ทดสอบ Activity Log ล่าสุด 50 รายการ
    @Test
    void activityLogTop50WorksWithPostgres() {

        String prefix = "TEST-" + UUID.randomUUID();
        LocalDateTime baseTime = LocalDateTime.of(2099, 1, 1, 0, 0);

        List<ActivityLog> logs = new ArrayList<>();

        for (int i = 0; i < 60; i++) {
            ActivityLog log = new ActivityLog();
            log.setId(prefix + "-" + i);
            log.setUserId("test-user");
            log.setUserName("Test User");
            log.setAction("TEST");
            log.setDetails("Activity log test " + i);
            log.setCreatedAt(baseTime.plusMinutes(i));

            logs.add(log);
        }

        try {
            activityLogRepository.saveAllAndFlush(logs);

            List<ActivityLog> results =
                    activityLogRepository.findTop50ByOrderByCreatedAtDesc();

            assertThat(results).hasSize(50);

            assertThat(results.get(0).getId())
                    .isEqualTo(prefix + "-59");

            assertThat(results.get(49).getId())
                    .isEqualTo(prefix + "-10");

            for (int i = 0; i < results.size() - 1; i++) {
                assertThat(results.get(i).getCreatedAt())
                        .isAfterOrEqualTo(
                                results.get(i + 1).getCreatedAt()
                        );
            }

        } finally {
            activityLogRepository.deleteAllInBatch(logs);
        }
    }

    // B3.5: ทดสอบ IN Query เมื่อส่ง List ว่าง
    @Test
    void bookingStatusInWithEmptyListWorksWithPostgres() {

        List<Booking> results =
                bookingRepository.findByRoomIdAndStatusIn(
                        "test-room-id",
                        List.of()
                );

        assertThat(results).isEmpty();
    }

    // B3.6: ทดสอบความละเอียด LocalDateTime กับ PostgreSQL
    @Test
    void postgresTimestampPrecisionIsMicroseconds() {

        LocalDateTime original =
                LocalDateTime.of(2026, 10, 25, 12, 0, 0, 123456789);

        LocalDateTime saved =
                jdbcTemplate.queryForObject(
                        "SELECT CAST(? AS timestamp)",
                        LocalDateTime.class,
                        original
                );

        assertThat(saved).isNotNull();

        assertThat(saved.getNano() % 1000).isZero();

        assertThat(saved).isNotEqualTo(original);
    }

    // B3.1: ทดสอบสร้าง Lease จาก Booking เดียวกันพร้อมกัน 2 Threads
    @Test
    void concurrentLeaseCreationDoesNotCreateDuplicates() throws Exception {

        String suffix = UUID.randomUUID().toString();
        String roomId = "test-room-" + suffix;
        String bookingId = "test-booking-" + suffix;
        String email = "lease-test-" + suffix + "@example.com";

        Room room = new Room();
        room.setId(roomId);
        room.setRoomNumber("TEST-" + suffix);
        room.setFloor(1);
        room.setStatus("Available");
        room.setPrice(5000.0);
        roomRepository.saveAndFlush(room);

        Booking booking = new Booking();
        booking.setId(bookingId);
        booking.setBookingNo("BK-" + suffix);
        booking.setRoomId(roomId);
        booking.setRoomNumber(room.getRoomNumber());
        booking.setGuestName("Test User");
        booking.setGuestEmail(email);
        booking.setGuestPhone("0812345678");
        booking.setCheckIn("2026-11-01");
        booking.setCheckOut("2027-10-31");
        booking.setTotalPrice(5000.0);
        booking.setStatus("Approved");
        booking.setCreatedAt(LocalDateTime.now());
        bookingRepository.saveAndFlush(booking);

        BookingLeaseService.LeaseApplication application =
                new BookingLeaseService.LeaseApplication(
                        "Test User",
                        "0812345678",
                        email,
                        "TEST-ID",
                        "0812345678"
                );

        CountDownLatch start = new CountDownLatch(1);

        String leaseId = null;
        String tenantId = null;

        try {
            CompletableFuture<Booking> first =
                    CompletableFuture.supplyAsync(() -> {
                        try {
                            start.await();
                            return bookingLeaseService.createLease(
                                    bookingId, email, application);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(e);
                        }
                    });

            CompletableFuture<Booking> second =
                    CompletableFuture.supplyAsync(() -> {
                        try {
                            start.await();
                            return bookingLeaseService.createLease(
                                    bookingId, email, application);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(e);
                        }
                    });

            // เริ่มทั้งสอง Threads พร้อมกัน
            start.countDown();

            Booking result1 = first.get(20, TimeUnit.SECONDS);
            Booking result2 = second.get(20, TimeUnit.SECONDS);

            // ทั้งสองคำขอต้องได้ Lease ID เดียวกัน
            assertThat(result1.getLeaseId()).isNotNull();
            assertThat(result2.getLeaseId())
                    .isEqualTo(result1.getLeaseId());

            leaseId = result1.getLeaseId();
            tenantId = result1.getTenantId();

            // Booking ต้องเปลี่ยนเป็น Completed
            Booking savedBooking =
                    bookingRepository.findById(bookingId).orElseThrow();

            assertThat(savedBooking.getStatus())
                    .isEqualTo("Completed");

            assertThat(savedBooking.getLeaseId())
                    .isEqualTo(leaseId);

            // Lease ต้องมีอยู่จริง
            Lease savedLease =
                    leaseRepository.findById(leaseId).orElseThrow();

            assertThat(savedLease.getRoomId())
                    .isEqualTo(roomId);

            // ต้องมี Active Lease สำหรับห้องนี้เพียงรายการเดียว
            assertThat(
                    leaseRepository.findByRoomIdAndStatus(roomId, "Active")
            ).hasSize(1);

            // สถานะห้องต้องเป็น Occupied
            Room savedRoom =
                    roomRepository.findById(roomId).orElseThrow();

            assertThat(savedRoom.getStatus())
                    .isEqualTo("Occupied");

        } finally {
            start.countDown();

            // ลบข้อมูลทดสอบ
            if (leaseId != null) {
                leaseRepository.deleteById(leaseId);
            }

            bookingRepository.deleteById(bookingId);

            if (tenantId != null) {
                tenantRepository.deleteById(tenantId);
            }

            roomRepository.deleteById(roomId);
        }
    }
}
