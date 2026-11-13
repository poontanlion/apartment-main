package com.victory.apartment.repository;

import com.victory.apartment.model.MaintenancePrediction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Optional;

public interface MaintenancePredictionRepository extends JpaRepository<MaintenancePrediction, String> {
    Optional<MaintenancePrediction> findByRoomIdAndTaskTypeId(String roomId, String taskTypeId);

    @Modifying
    @Transactional
    @Query("delete from MaintenancePrediction p where p.roomId = :roomId")
    void deleteByRoomId(@Param("roomId") String roomId);

    @Modifying
    @Transactional
    @Query("delete from MaintenancePrediction p where p.roomId in :roomIds")
    void deleteByRoomIdIn(@Param("roomIds") Collection<String> roomIds);
}
