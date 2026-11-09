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

    /** Where tenants send payment; printed on invoices. All optional. */
    @Column(name = "bank_name")
    private String bankName;
    @Column(name = "bank_account_name")
    private String accountName;
    @Column(name = "bank_account_number")
    private String accountNumber;
    @Column(name = "bank_qr_image", columnDefinition = "text")
    private String qrCodeImage; // image data URL

    public BillingSettings() {}
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Double getMonthlyInterestRate() { return monthlyInterestRate; }
    public void setMonthlyInterestRate(Double monthlyInterestRate) { this.monthlyInterestRate = monthlyInterestRate; }
    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }
    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public String getQrCodeImage() { return qrCodeImage; }
    public void setQrCodeImage(String qrCodeImage) { this.qrCodeImage = qrCodeImage; }
    public java.time.LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(java.time.LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
