package com.victory.apartment.repository;

import com.victory.apartment.model.RoomPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RoomPriceRepository extends JpaRepository<RoomPrice, String> {
    List<RoomPrice> findByRoomIdOrderByEffectiveMonthDesc(String roomId);
    Optional<RoomPrice> findFirstByRoomIdAndEffectiveMonthLessThanEqualOrderByEffectiveMonthDesc(String roomId, LocalDate month);
    Optional<RoomPrice> findByRoomIdAndEffectiveMonth(String roomId, LocalDate effectiveMonth);
    List<RoomPrice> findByEffectiveMonthLessThanEqualOrderByRoomIdAscEffectiveMonthDesc(LocalDate month);
}
