package com.victory.apartment.repository;

import com.victory.apartment.model.BillPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BillPaymentRepository extends JpaRepository<BillPayment, String> {
    List<BillPayment> findByBillIdOrderByPaidAtAsc(String billId);
}
