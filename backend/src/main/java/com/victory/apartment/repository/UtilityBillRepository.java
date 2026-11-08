package com.victory.apartment.repository;

import com.victory.apartment.model.UtilityBill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;

public interface UtilityBillRepository extends JpaRepository<UtilityBill, String> {
    List<UtilityBill> findAllByOrderByCreatedAtDesc();
    List<UtilityBill> findByRoomIdOrderByCreatedAtDesc(String roomId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from UtilityBill b where b.id = :id")
    Optional<UtilityBill> findForUpdate(@Param("id") String id);
}
