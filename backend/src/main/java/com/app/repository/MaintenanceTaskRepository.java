package com.app.repository;

import com.app.model.MaintenanceTask;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface MaintenanceTaskRepository extends JpaRepository<MaintenanceTask, String> {
    List<MaintenanceTask> findAllByOrderByCreatedAtDesc();
    List<MaintenanceTask> findByReporterEmailIgnoreCaseOrderByCreatedAtDesc(String reporterEmail);
    List<MaintenanceTask> findByRoomNumberOrderByCreatedAtDesc(String roomNumber);
    Optional<MaintenanceTask> findByTaskNo(String taskNo);
}
