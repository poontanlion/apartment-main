package com.victory.apartment.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "bill_payments")
public class BillPayment {
    @Id
    private String id;
    @Column(name = "bill_id", nullable = false)
    private String billId;
    @Column(nullable = false)
    private Double amount;
    @Column(name = "payment_date", nullable = false)
    private LocalDateTime paymentDate;
    @Column(length = 1000)
    private String note;
    @Column(name = "proof_image", columnDefinition = "text")
    private String proofImage;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getBillId() { return billId; }
    public void setBillId(String billId) { this.billId = billId; }
    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
    public LocalDateTime getPaymentDate() { return paymentDate; }
    public void setPaymentDate(LocalDateTime paymentDate) { this.paymentDate = paymentDate; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getProofImage() { return proofImage; }
    public void setProofImage(String proofImage) { this.proofImage = proofImage; }
}
