package com.victory.apartment.repository;

import com.victory.apartment.model.MaintenancePrediction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MaintenancePredictionRepository extends JpaRepository<MaintenancePrediction, String> {
    Optional<MaintenancePrediction> findByRoomIdAndTaskTypeId(String roomId, String taskTypeId);
}
