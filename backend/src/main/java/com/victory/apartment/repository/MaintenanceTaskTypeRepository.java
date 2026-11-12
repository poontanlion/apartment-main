package com.victory.apartment.repository;

import com.victory.apartment.model.MaintenanceTaskType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MaintenanceTaskTypeRepository extends JpaRepository<MaintenanceTaskType, String> {
    Optional<MaintenanceTaskType> findByCode(String code);
}
