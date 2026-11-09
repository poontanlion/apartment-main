package com.victory.apartment.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "utility_bills")
public class UtilityBill {

    @Id
    private String id;

    @Column(unique = true)
    private String invoiceNo;

    private String leaseId;
    private String roomId;
    private String roomNumber;
    private String tenantName;
    private String billingMonth;
    private Double rentAmount;
    private Double prevWaterMeter;
    private Double currWaterMeter;
    private Double waterRate;
    private Double waterAmount;
    private Double prevElectricMeter;
    private Double currElectricMeter;
    private Double electricRate;
    private Double electricAmount;
    private Double commonFee;
    private String extraChargeLabel;
    private Double extraChargeAmount;
    private Double totalAmount; // Charges for this billing month only
    @Column(name = "previous_outstanding", nullable = false)
    private Double balanceForwardAmount = 0.0;
    @Column(nullable = false)
    private Double interestRate = 0.0;
    @Column(nullable = false)
    private Double interestAmount = 0.0;
    @Column(name = "total_due", nullable = false)
    private Double amountDue;
    @Column(name = "paid_amount", nullable = false)
    private Double paidAmount = 0.0;
    @Column(name = "outstanding_balance", nullable = false)
    private Double balanceDue;
    private String status; // Unpaid, Partial, Paid
    private String paymentDate;
    @Column(columnDefinition = "text")
    private String slipImage;
    @Column(columnDefinition = "text")
    private String scannedDocument;
    private LocalDateTime createdAt;

    public UtilityBill() {}

    @PrePersist
    private void initializeBillingBalances() {
        if (balanceForwardAmount == null) balanceForwardAmount = 0.0;
        if (interestRate == null) interestRate = 0.0;
        if (interestAmount == null) interestAmount = 0.0;
        if (paidAmount == null) paidAmount = 0.0;
        if (amountDue == null) {
            amountDue = (totalAmount != null ? totalAmount : 0.0) + balanceForwardAmount + interestAmount;
        }
        if (balanceDue == null) balanceDue = Math.max(0.0, amountDue - paidAmount);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getInvoiceNo() { return invoiceNo; }
    public void setInvoiceNo(String invoiceNo) { this.invoiceNo = invoiceNo; }
    public String getLeaseId() { return leaseId; }
    public void setLeaseId(String leaseId) { this.leaseId = leaseId; }
    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }
    public String getRoomNumber() { return roomNumber; }
    public void setRoomNumber(String roomNumber) { this.roomNumber = roomNumber; }
    public String getTenantName() { return tenantName; }
    public void setTenantName(String tenantName) { this.tenantName = tenantName; }
    public String getBillingMonth() { return billingMonth; }
    public void setBillingMonth(String billingMonth) { this.billingMonth = billingMonth; }
    public Double getRentAmount() { return rentAmount; }
    public void setRentAmount(Double rentAmount) { this.rentAmount = rentAmount; }
    public Double getPrevWaterMeter() { return prevWaterMeter; }
    public void setPrevWaterMeter(Double prevWaterMeter) { this.prevWaterMeter = prevWaterMeter; }
    public Double getCurrWaterMeter() { return currWaterMeter; }
    public void setCurrWaterMeter(Double currWaterMeter) { this.currWaterMeter = currWaterMeter; }
    public Double getWaterRate() { return waterRate; }
    public void setWaterRate(Double waterRate) { this.waterRate = waterRate; }
    public Double getWaterAmount() { return waterAmount; }
    public void setWaterAmount(Double waterAmount) { this.waterAmount = waterAmount; }
    public Double getPrevElectricMeter() { return prevElectricMeter; }
    public void setPrevElectricMeter(Double prevElectricMeter) { this.prevElectricMeter = prevElectricMeter; }
    public Double getCurrElectricMeter() { return currElectricMeter; }
    public void setCurrElectricMeter(Double currElectricMeter) { this.currElectricMeter = currElectricMeter; }
    public Double getElectricRate() { return electricRate; }
    public void setElectricRate(Double electricRate) { this.electricRate = electricRate; }
    public Double getElectricAmount() { return electricAmount; }
    public void setElectricAmount(Double electricAmount) { this.electricAmount = electricAmount; }
    public Double getCommonFee() { return commonFee; }
    public void setCommonFee(Double commonFee) { this.commonFee = commonFee; }
    public String getExtraChargeLabel() { return extraChargeLabel; }
    public void setExtraChargeLabel(String extraChargeLabel) { this.extraChargeLabel = extraChargeLabel; }
    public Double getExtraChargeAmount() { return extraChargeAmount; }
    public void setExtraChargeAmount(Double extraChargeAmount) { this.extraChargeAmount = extraChargeAmount; }
    public Double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }
    public Double getBalanceForwardAmount() { return balanceForwardAmount; }
    public void setBalanceForwardAmount(Double balanceForwardAmount) { this.balanceForwardAmount = balanceForwardAmount; }
    public Double getInterestRate() { return interestRate; }
    public void setInterestRate(Double interestRate) { this.interestRate = interestRate; }
    public Double getInterestAmount() { return interestAmount; }
    public void setInterestAmount(Double interestAmount) { this.interestAmount = interestAmount; }
    public Double getAmountDue() { return amountDue; }
    public void setAmountDue(Double amountDue) { this.amountDue = amountDue; }
    public Double getPaidAmount() { return paidAmount; }
    public void setPaidAmount(Double paidAmount) { this.paidAmount = paidAmount; }
    public Double getBalanceDue() { return balanceDue; }
    public void setBalanceDue(Double balanceDue) { this.balanceDue = balanceDue; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPaymentDate() { return paymentDate; }
    public void setPaymentDate(String paymentDate) { this.paymentDate = paymentDate; }
    public String getSlipImage() { return slipImage; }
    public void setSlipImage(String slipImage) { this.slipImage = slipImage; }
    public String getScannedDocument() { return scannedDocument != null ? scannedDocument : slipImage; }
    public void setScannedDocument(String scannedDocument) { this.scannedDocument = scannedDocument; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
