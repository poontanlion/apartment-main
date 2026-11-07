package com.victory.apartment.model;

import jakarta.persistence.*;

@Entity
@Table(name = "utility_bill_payments")
public class BillPayment {
    @Id
    private String id;
    @Column(nullable = false)
    private String billId;
    @Column(nullable = false)
    private Double amount;
    @Column(name = "payment_date")
    private String paidAt;
    @Column(name = "note", columnDefinition = "text")
    private String slipImage;
    @Column(name = "created_at")
    private java.time.LocalDateTime createdAt;

    public BillPayment() {}
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getBillId() { return billId; }
    public void setBillId(String billId) { this.billId = billId; }
    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
    public String getPaidAt() { return paidAt; }
    public void setPaidAt(String paidAt) { this.paidAt = paidAt; }
    public String getSlipImage() { return slipImage; }
    public void setSlipImage(String slipImage) { this.slipImage = slipImage; }
    public java.time.LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; }
}
