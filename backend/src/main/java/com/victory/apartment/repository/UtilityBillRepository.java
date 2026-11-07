package com.victory.apartment.repository;

import com.victory.apartment.model.UtilityBill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface UtilityBillRepository extends JpaRepository<UtilityBill, String> {
    List<UtilityBill> findAllByOrderByCreatedAtDesc();

    @Query("""
        select new com.victory.apartment.repository.BillReportRow(
            b.id, b.leaseId, b.roomId, b.roomNumber, b.tenantName, b.billingMonth,
            b.rentAmount, b.waterAmount, b.electricAmount, b.commonFee, b.totalAmount, b.status,
            l.tenantId, l.tenantName, r.buildingId)
        from UtilityBill b
        left join Lease l on l.id = b.leaseId
        left join Room r on r.id = b.roomId
        """)
    List<BillReportRow> findAllForReport();
}
