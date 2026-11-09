package com.victory.apartment.controller;

import com.victory.apartment.model.*;
import com.victory.apartment.repository.*;
import com.victory.apartment.service.ActivityLogService;
import com.victory.apartment.service.BillAmounts;
import com.victory.apartment.service.PriceRuleException;
import com.victory.apartment.service.RentQuoteService;
import com.victory.apartment.service.RentQuoteService.RentQuote;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/utility-bills")
public class UtilityBillController {
    private final UtilityBillRepository repo;
    private final RoomRepository roomRepo;
    private final BillPaymentRepository paymentRepo;
    private final BillingSettingsRepository settingsRepo;
    private final ActivityLogService logService;
    private final RentQuoteService rentQuotes;

    public UtilityBillController(UtilityBillRepository repo, RoomRepository roomRepo,
            BillPaymentRepository paymentRepo, BillingSettingsRepository settingsRepo,
            ActivityLogService logService, RentQuoteService rentQuotes) {
        this.repo = repo;
        this.roomRepo = roomRepo;
        this.paymentRepo = paymentRepo;
        this.settingsRepo = settingsRepo;
        this.logService = logService;
        this.rentQuotes = rentQuotes;
    }

    @GetMapping
    public List<UtilityBill> getAll(@RequestParam(required = false) String buildingId) {
        List<UtilityBill> bills = repo.findAllByOrderByCreatedAtDesc();
        if (buildingId != null && !buildingId.isEmpty() && !"All".equalsIgnoreCase(buildingId)) {
            List<String> roomIds = roomRepo.findByBuildingIdOrderByRoomNumberAsc(buildingId).stream().map(Room::getId).toList();
            return bills.stream().filter(b -> b.getRoomId() != null && roomIds.contains(b.getRoomId())).toList();
        }
        return bills;
    }

    @PostMapping
    public UtilityBill create(@RequestBody UtilityBill bill) {
        if (bill.getId() == null || bill.getId().isEmpty()) {
            bill.setId("bill-" + UUID.randomUUID().toString().substring(0, 8));
        } else if (repo.existsById(bill.getId())) {
            throw new PriceRuleException(HttpStatus.CONFLICT, "BILL_ALREADY_EXISTS", "A bill with this id already exists.");
        }
        if (bill.getCreatedAt() == null) bill.setCreatedAt(LocalDateTime.now());
        YearMonth targetMonth = RentQuoteService.parseBillingMonth(bill.getBillingMonth());
        boolean monthAlreadyBilled = repo.findByRoomIdOrderByCreatedAtDesc(bill.getRoomId()).stream()
                .anyMatch(old -> safeMonth(old.getBillingMonth()).equals(targetMonth));
        if (monthAlreadyBilled) throw new PriceRuleException(HttpStatus.CONFLICT, "DUPLICATE_MONTH_BILL", "This room already has a bill for the selected month.");
        RentQuote quote;
        try {
            quote = rentQuotes.quote(bill.getRoomId(), targetMonth);
        } catch (PriceRuleException e) {
            if ("RENT_UNAVAILABLE".equals(e.getCode())
                    && !rentQuotes.hasTenantForBillingMonth(bill.getRoomId(), targetMonth)) {
                throw new PriceRuleException(HttpStatus.BAD_REQUEST, "NO_TENANT_FOR_BILLING_MONTH",
                        "Cannot issue a bill because no lease covers this room for the selected month.");
            }
            throw e;
        }
        if (!"LEASE".equals(quote.source())
                && !rentQuotes.hasTenantForBillingMonth(bill.getRoomId(), targetMonth)) {
            throw new PriceRuleException(HttpStatus.BAD_REQUEST, "NO_TENANT_FOR_BILLING_MONTH",
                    "Cannot issue a bill because no lease covers this room for the selected month.");
        }
        bill.setRentAmount(quote.rentAmount());
        bill.setLeaseId(quote.leaseId());
        applyExtraCharge(bill);
        calculateAmounts(bill);

        UtilityBill previous = repo.findByRoomIdOrderByCreatedAtDesc(bill.getRoomId()).stream()
                .filter(old -> safeMonth(old.getBillingMonth()).isBefore(targetMonth))
                .max(Comparator.comparing(old -> safeMonth(old.getBillingMonth())))
                .orElse(null);
        double arrears = previous == null ? 0 : outstanding(previous);
        double interestRate = settingsRepo.findById("default").map(BillingSettings::getMonthlyInterestRate).orElse(0.0);
        double interestAmount = roundMoney(arrears * interestRate / 100.0);
        bill.setBalanceForwardAmount(arrears);
        bill.setInterestRate(interestRate);
        bill.setInterestAmount(interestAmount);
        bill.setAmountDue(roundMoney(nz(bill.getTotalAmount()) + arrears + interestAmount));
        bill.setPaidAmount(0.0);
        bill.setBalanceDue(bill.getAmountDue());
        bill.setStatus("Unpaid");

        UtilityBill saved = repo.save(bill);
        if (saved.getRoomId() != null) {
            roomRepo.findById(saved.getRoomId()).ifPresent(room -> {
                room.setPrevWaterMeter(saved.getCurrWaterMeter());
                room.setPrevElectricMeter(saved.getCurrElectricMeter());
                roomRepo.save(room);
            });
        }
        logService.log("Utility Bill Created", String.format("Created bill %s for Unit %s (%.0f THB due)", saved.getInvoiceNo(), saved.getRoomNumber(), saved.getAmountDue()));
        return saved;
    }

    @PutMapping("/{id}")
    public ResponseEntity<UtilityBill> update(@PathVariable String id, @RequestBody UtilityBill bill) {
        return repo.findById(id).map(stored -> {
            bill.setId(id);
            bill.setBillingMonth(stored.getBillingMonth());
            bill.setRentAmount(stored.getRentAmount() != null ? stored.getRentAmount() : rentInsideTotal(stored));
            bill.setBalanceForwardAmount(nz(stored.getBalanceForwardAmount()));
            bill.setInterestRate(nz(stored.getInterestRate()));
            bill.setInterestAmount(nz(stored.getInterestAmount()));
            bill.setPaidAmount(nz(stored.getPaidAmount()));
            if (bill.getExtraChargeAmount() == null) {
                bill.setExtraChargeAmount(stored.getExtraChargeAmount());
                bill.setExtraChargeLabel(stored.getExtraChargeLabel());
            }
            validateExtraCharge(bill);
            calculateAmounts(bill);
            bill.setAmountDue(roundMoney(nz(bill.getTotalAmount()) + nz(bill.getBalanceForwardAmount()) + nz(bill.getInterestAmount())));
            bill.setBalanceDue(Math.max(0, roundMoney(bill.getAmountDue() - bill.getPaidAmount())));
            bill.setStatus(statusFor(bill.getPaidAmount(), bill.getBalanceDue()));
            return ResponseEntity.ok(repo.save(bill));
        }).orElse(ResponseEntity.notFound().build());
    }

    private double rentInsideTotal(UtilityBill bill) {
        return BillAmounts.rentInsideTotal(bill.getTotalAmount(), bill.getWaterAmount(), bill.getElectricAmount(), bill.getCommonFee());
    }

    @GetMapping("/rent-quote")
    public RentQuote rentQuote(@RequestParam String roomId, @RequestParam String month) {
        return rentQuotes.quote(roomId, RentQuoteService.parseBillingMonth(month));
    }

    private void calculateAmounts(UtilityBill bill) {
        double waterUnits = Math.max(0, nz(bill.getCurrWaterMeter()) - nz(bill.getPrevWaterMeter()));
        double electricUnits = Math.max(0, nz(bill.getCurrElectricMeter()) - nz(bill.getPrevElectricMeter()));
        bill.setWaterAmount(waterUnits * (bill.getWaterRate() != null ? bill.getWaterRate() : 9.0));
        bill.setElectricAmount(electricUnits * (bill.getElectricRate() != null ? bill.getElectricRate() : 4.0));
        bill.setTotalAmount(nz(bill.getRentAmount()) + nz(bill.getWaterAmount()) + nz(bill.getElectricAmount())
                + (bill.getCommonFee() != null ? bill.getCommonFee() : 300) + nz(bill.getExtraChargeAmount()));
    }

    /** A new bill takes the room's extra charge unless the request names one (zero means none this month). */
    private void applyExtraCharge(UtilityBill bill) {
        if (bill.getExtraChargeAmount() == null) {
            roomRepo.findById(bill.getRoomId()).ifPresent(room -> {
                bill.setExtraChargeLabel(room.getExtraChargeLabel());
                bill.setExtraChargeAmount(room.getExtraChargeAmount());
            });
        }
        validateExtraCharge(bill);
    }

    private void validateExtraCharge(UtilityBill bill) {
        double amount = nz(bill.getExtraChargeAmount());
        if (Double.isNaN(amount) || Double.isInfinite(amount) || amount < 0) {
            throw new PriceRuleException(HttpStatus.BAD_REQUEST, "INVALID_EXTRA_CHARGE", "Extra charge must be zero or more.");
        }
        if (amount == 0) {
            bill.setExtraChargeAmount(null);
            bill.setExtraChargeLabel(null);
            return;
        }
        String label = bill.getExtraChargeLabel() == null ? "" : bill.getExtraChargeLabel().trim();
        if (label.length() > 100) {
            throw new PriceRuleException(HttpStatus.BAD_REQUEST, "INVALID_EXTRA_CHARGE_LABEL", "Extra charge name must be at most 100 characters.");
        }
        bill.setExtraChargeAmount(roundMoney(amount));
        bill.setExtraChargeLabel(label.isEmpty() ? "Other charges" : label);
    }

    @GetMapping("/{id}/payments")
    public List<BillPayment> getPayments(@PathVariable String id) {
        if (!repo.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Bill not found.");
        return paymentRepo.findByBillIdOrderByPaymentDateDesc(id);
    }

    @Transactional
    @PostMapping("/{id}/payments")
    public Map<String, Object> recordPayment(@PathVariable String id, @RequestBody Map<String, Object> request) {
        UtilityBill bill = repo.findForUpdate(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bill not found."));
        YearMonth billMonth = safeMonth(bill.getBillingMonth());
        boolean balanceRolledForward = repo.findByRoomIdOrderByCreatedAtDesc(bill.getRoomId()).stream()
                .anyMatch(other -> !other.getId().equals(id) && safeMonth(other.getBillingMonth()).isAfter(billMonth));
        if (balanceRolledForward) throw new ResponseStatusException(HttpStatus.CONFLICT, "This balance was carried into a newer bill. Record payment against the latest invoice.");
        Object rawAmount = request.get("amount");
        if (!(rawAmount instanceof Number)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid payment amount.");
        double amount = roundMoney(((Number) rawAmount).doubleValue());
        double balance = outstanding(bill);
        if (amount <= 0 || amount > balance + 0.001) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment must be greater than zero and no more than the remaining balance.");
        BillPayment payment = new BillPayment();
        payment.setId("pay-" + UUID.randomUUID().toString().substring(0, 12));
        payment.setBillId(id);
        payment.setAmount(amount);
        payment.setPaymentDate(parsePaidAt(request.get("paidAt")).atStartOfDay());
        if (request.get("note") instanceof String note && !note.isBlank()) payment.setNote(note);
        if (request.get("proofImage") instanceof String proof) payment.setProofImage(proof);
        else if (request.get("slipImage") instanceof String slip) payment.setProofImage(slip);
        paymentRepo.save(payment);

        double paid = nz(bill.getPaidAmount()) + amount;
        bill.setPaidAmount(roundMoney(paid));
        bill.setAmountDue(nz(bill.getAmountDue()) > 0 ? bill.getAmountDue() : nz(bill.getTotalAmount()));
        bill.setBalanceDue(Math.max(0, roundMoney(bill.getAmountDue() - paid)));
        bill.setStatus(statusFor(paid, bill.getBalanceDue()));
        bill.setPaymentDate(bill.getBalanceDue() == 0 ? payment.getPaymentDate().toLocalDate().toString() : null);
        UtilityBill saved = repo.save(bill);
        logService.log("Bill Payment Recorded", String.format("Recorded %.2f THB for bill %s; remaining %.2f THB", amount, bill.getInvoiceNo(), bill.getBalanceDue()));
        return Map.of("bill", saved, "payment", payment);
    }

    /** Kept for existing document/slip updates and older clients. */
    @PutMapping("/{id}/status")
    public ResponseEntity<UtilityBill> updateStatus(@PathVariable String id, @RequestBody Map<String, String> req) {
        return repo.findById(id).map(bill -> {
            // Keep older clients working: a legacy "Paid" action becomes a full-payment record.
            if ("Paid".equals(req.get("status")) && !"Paid".equals(bill.getStatus())) {
                double due = outstanding(bill);
                if (due > 0) {
                    BillPayment payment = new BillPayment();
                    payment.setId("pay-" + UUID.randomUUID().toString().substring(0, 12));
                    payment.setBillId(id);
                    payment.setAmount(due);
                    payment.setPaymentDate(LocalDateTime.now());
                    if (req.get("slipImage") != null) payment.setProofImage(req.get("slipImage"));
                    paymentRepo.save(payment);
                    bill.setPaidAmount(nz(bill.getPaidAmount()) + due);
                    bill.setBalanceDue(0.0);
                    bill.setStatus("Paid");
                    bill.setPaymentDate(payment.getPaymentDate().toLocalDate().toString());
                }
            }
            if (req.containsKey("slipImage")) bill.setSlipImage(req.get("slipImage"));
            if (req.containsKey("scannedDocument")) bill.setScannedDocument(req.get("scannedDocument"));
            logService.log("Bill Documents Updated", String.format("Updated proof documents for bill %s", bill.getInvoiceNo()));
            return ResponseEntity.ok(repo.save(bill));
        }).orElse(ResponseEntity.notFound().build());
    }

    private LocalDate parsePaidAt(Object value) {
        try { return value instanceof String s && !s.isBlank() ? LocalDate.parse(s) : LocalDate.now(); }
        catch (Exception ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment date must be a valid date."); }
    }
    private YearMonth safeMonth(String value) {
        try { return YearMonth.parse(value, DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)); }
        catch (Exception ex) { return YearMonth.of(1, 1); }
    }
    private double outstanding(UtilityBill bill) {
        if (bill.getBalanceDue() != null) return Math.max(0, bill.getBalanceDue());
        if (bill.getAmountDue() != null) return Math.max(0, bill.getAmountDue() - nz(bill.getPaidAmount()));
        return Math.max(0, nz(bill.getTotalAmount()) - nz(bill.getPaidAmount()));
    }
    private String statusFor(double paid, double due) {
        if (due <= 0.001) return "Paid";
        return paid > 0 ? "Partial" : "Unpaid";
    }
    private double nz(Double v) { return v == null ? 0 : v; }
    private double roundMoney(double value) { return Math.round(value * 100.0) / 100.0; }
}
