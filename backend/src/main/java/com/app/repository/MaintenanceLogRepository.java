package com.app.repository;

import com.app.model.MaintenanceLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MaintenanceLogRepository extends JpaRepository<MaintenanceLog, String> {
    List<MaintenanceLog> findByRoomIdOrderByDateDesc(String roomId);
    List<MaintenanceLog> findByTaskNo(String taskNo);
}
