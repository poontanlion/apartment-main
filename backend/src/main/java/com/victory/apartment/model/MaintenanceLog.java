package com.victory.apartment.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "maintenance_logs")
public class MaintenanceLog {

    @Id
    private String id;

    private String roomId;
    private String roomNumber;
    private String date;
    private String taskNo;
    private String category;
    private String taskTypeId;
    private LocalDate completedDate;
    @Column(length = 1000)
    private String description;
    @Column(columnDefinition = "text")
    private String suppliesSummary;
    private Double totalCost;
    private String performedBy;
    @Column(columnDefinition = "text")
    private String scannedDocument;

    public MaintenanceLog() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }
    public String getRoomNumber() { return roomNumber; }
    public void setRoomNumber(String roomNumber) { this.roomNumber = roomNumber; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public String getTaskNo() { return taskNo; }
    public void setTaskNo(String taskNo) { this.taskNo = taskNo; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getTaskTypeId() { return taskTypeId; }
    public void setTaskTypeId(String taskTypeId) { this.taskTypeId = taskTypeId; }
    public LocalDate getCompletedDate() { return completedDate; }
    public void setCompletedDate(LocalDate completedDate) { this.completedDate = completedDate; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getSuppliesSummary() { return suppliesSummary; }
    public void setSuppliesSummary(String suppliesSummary) { this.suppliesSummary = suppliesSummary; }
    public Double getTotalCost() { return totalCost; }
    public void setTotalCost(Double totalCost) { this.totalCost = totalCost; }
    public String getPerformedBy() { return performedBy; }
    public void setPerformedBy(String performedBy) { this.performedBy = performedBy; }

    public String getScannedDocument() { return scannedDocument; }
    public void setScannedDocument(String scannedDocument) { this.scannedDocument = scannedDocument; }
}

