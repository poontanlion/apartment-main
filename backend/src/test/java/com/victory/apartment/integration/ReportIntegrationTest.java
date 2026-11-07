package com.victory.apartment.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.victory.apartment.TestcontainersConfiguration;
import com.victory.apartment.model.Lease;
import com.victory.apartment.model.Room;
import com.victory.apartment.model.UtilityBill;
import com.victory.apartment.repository.BillReportRow;
import com.victory.apartment.repository.LeaseRepository;
import com.victory.apartment.repository.RoomRepository;
import com.victory.apartment.repository.UtilityBillRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ReportIntegrationTest {

    @Autowired private UtilityBillRepository bills;
    @Autowired private RoomRepository rooms;
    @Autowired private LeaseRepository leases;

    private Room saveRoom(String buildingId) {
        String suffix = UUID.randomUUID().toString();
        Room room = new Room();
        room.setId("report-room-" + suffix);
        room.setRoomNumber("REPORT-" + suffix);
        room.setFloor(1);
        room.setBuildingId(buildingId);
        return rooms.save(room);
    }

    private Lease saveLease(Room room, String tenantId, String tenantName) {
        Lease lease = new Lease();
        lease.setId("report-lease-" + UUID.randomUUID());
        lease.setTenantId(tenantId);
        lease.setTenantName(tenantName);
        lease.setRoomId(room.getId());
        lease.setCheckInDate(LocalDate.of(2026, 1, 1));
        lease.setCheckOutDate(LocalDate.of(2026, 12, 31));
        return leases.save(lease);
    }

    private UtilityBill saveBill(Room room, String leaseId) {
        UtilityBill bill = new UtilityBill();
        bill.setId("report-bill-" + UUID.randomUUID());
        bill.setRoomId(room.getId());
        bill.setRoomNumber(room.getRoomNumber());
        bill.setLeaseId(leaseId);
        bill.setTenantName("Somchai");
        bill.setBillingMonth("August 2026");
        bill.setRentAmount(5000.0);
        bill.setWaterAmount(90.0);
        bill.setElectricAmount(400.0);
        bill.setCommonFee(300.0);
        bill.setTotalAmount(5790.0);
        bill.setStatus("Pending");
        return bills.save(bill);
    }

    private BillReportRow rowOf(UtilityBill bill) {
        return bills.findAllForReport().stream()
            .filter(row -> row.id().equals(bill.getId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No report row for " + bill.getId()));
    }

    @Test
    void findAllForReport_joinsLeaseAndRoom() {
        Room room = saveRoom("bld-report");
        Lease lease = saveLease(room, "tenant-report", "Somchai Jaidee");

        BillReportRow row = rowOf(saveBill(room, lease.getId()));

        assertThat(row.leaseTenantId()).isEqualTo("tenant-report");
        assertThat(row.leaseTenantName()).isEqualTo("Somchai Jaidee");
        assertThat(row.buildingId()).isEqualTo("bld-report");
        assertThat(row.roomNumber()).isEqualTo(room.getRoomNumber());
        assertThat(row.billingMonth()).isEqualTo("August 2026");
        assertThat(row.rentAmount()).isEqualTo(5000.0);
        assertThat(row.waterAmount()).isEqualTo(90.0);
        assertThat(row.electricAmount()).isEqualTo(400.0);
        assertThat(row.commonFee()).isEqualTo(300.0);
        assertThat(row.totalAmount()).isEqualTo(5790.0);
        assertThat(row.status()).isEqualTo("Pending");
    }

    @Test
    void findAllForReport_keepsBillWithoutLease() {
        BillReportRow row = rowOf(saveBill(saveRoom("bld-report"), null));

        assertThat(row.leaseTenantId()).isNull();
        assertThat(row.leaseTenantName()).isNull();
        assertThat(row.tenantName()).isEqualTo("Somchai");
    }

    @Test
    void findAllForReport_keepsBillOfDeletedRoom() {
        Room room = saveRoom("bld-report");
        UtilityBill bill = saveBill(room, null);
        rooms.deleteById(room.getId());

        BillReportRow row = rowOf(bill);

        assertThat(row.buildingId()).isNull();
        assertThat(row.roomNumber()).isEqualTo(room.getRoomNumber());
    }

    @Test
    void row_hasNoAttachmentFields() {
        assertThat(Arrays.stream(BillReportRow.class.getRecordComponents()).map(RecordComponent::getName))
            .doesNotContain("slipImage", "scannedDocument");
    }
}
