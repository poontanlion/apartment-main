package com.victory.apartment.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "maintenance_predictions", uniqueConstraints = @UniqueConstraint(name = "uk_maintenance_predictions_room_task", columnNames = {"room_id", "task_type_id"}))
public class MaintenancePrediction {

    @Id
    private String id;

    @Column(nullable = false)
    private String roomId;

    @Column(nullable = false)
    private String taskTypeId;

    private LocalDate predictedDate;

    private LocalDate overrideDate;

    private LocalDateTime computedAt;

    public MaintenancePrediction() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }

    public String getTaskTypeId() { return taskTypeId; }
    public void setTaskTypeId(String taskTypeId) { this.taskTypeId = taskTypeId; }

    public LocalDate getPredictedDate() { return predictedDate; }
    public void setPredictedDate(LocalDate predictedDate) { this.predictedDate = predictedDate; }

    public LocalDate getOverrideDate() { return overrideDate; }
    public void setOverrideDate(LocalDate overrideDate) { this.overrideDate = overrideDate; }

    public LocalDateTime getComputedAt() { return computedAt; }
    public void setComputedAt(LocalDateTime computedAt) { this.computedAt = computedAt; }
}
