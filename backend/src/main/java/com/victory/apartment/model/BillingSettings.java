package com.victory.apartment.model;

import jakarta.persistence.*;

@Entity
@Table(name = "interest_rate_configs")
public class BillingSettings {
    @Id
    private String id = "default";
    @Column(name = "rate", nullable = false)
    private Double monthlyInterestRate = 0.0;
    @Column(name = "updated_at")
    private java.time.LocalDateTime updatedAt;

    public BillingSettings() {}
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Double getMonthlyInterestRate() { return monthlyInterestRate; }
    public void setMonthlyInterestRate(Double monthlyInterestRate) { this.monthlyInterestRate = monthlyInterestRate; }
    public java.time.LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(java.time.LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
