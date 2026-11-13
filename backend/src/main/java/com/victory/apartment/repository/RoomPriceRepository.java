package com.victory.apartment.repository;

import com.victory.apartment.model.RoomPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoomPriceRepository extends JpaRepository<RoomPrice, String> {
    List<RoomPrice> findByRoomIdOrderByEffectiveMonthDesc(String roomId);
    Optional<RoomPrice> findFirstByRoomIdAndEffectiveMonthLessThanEqualOrderByEffectiveMonthDesc(String roomId, LocalDate month);
    Optional<RoomPrice> findByRoomIdAndEffectiveMonth(String roomId, LocalDate effectiveMonth);
    List<RoomPrice> findByEffectiveMonthLessThanEqualOrderByRoomIdAscEffectiveMonthDesc(LocalDate month);

    @Modifying
    @Transactional
    @Query("delete from RoomPrice p where p.roomId = :roomId")
    void deleteByRoomId(@Param("roomId") String roomId);

    @Modifying
    @Transactional
    @Query("delete from RoomPrice p where p.roomId in :roomIds")
    void deleteByRoomIdIn(@Param("roomIds") Collection<String> roomIds);
}
