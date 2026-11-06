package com.victory.apartment.controller;

import com.victory.apartment.model.UtilityBill;
import com.victory.apartment.model.Room;
import com.victory.apartment.repository.UtilityBillRepository;
import com.victory.apartment.repository.RoomRepository;
import com.victory.apartment.service.ActivityLogService;
import com.victory.apartment.service.PriceRuleException;
import com.victory.apartment.service.RentQuoteService;
import com.victory.apartment.service.RentQuoteService.RentQuote;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/utility-bills")
public class UtilityBillController {

    private final UtilityBillRepository repo;
    private final RoomRepository roomRepo;
    private final ActivityLogService logService;
    private final RentQuoteService rentQuotes;

    public UtilityBillController(UtilityBillRepository repo, RoomRepository roomRepo, ActivityLogService logService,
                                 RentQuoteService rentQuotes) {
        this.repo = repo;
        this.roomRepo = roomRepo;
        this.logService = logService;
        this.rentQuotes = rentQuotes;
    }

    @GetMapping
    public List<UtilityBill> getAll(@RequestParam(required = false) String buildingId) {
        List<UtilityBill> bills = repo.findAllByOrderByCreatedAtDesc();
        if (buildingId != null && !buildingId.isEmpty() && !"All".equalsIgnoreCase(buildingId)) {
            List<String> roomIds = roomRepo.findByBuildingIdOrderByRoomNumberAsc(buildingId)
                    .stream().map(Room::getId).toList();
            return bills.stream().filter(b -> b.getRoomId() != null && roomIds.contains(b.getRoomId())).toList();
        }
        return bills;
    }

    @PostMapping
    public UtilityBill create(@RequestBody UtilityBill bill) {
        if (bill.getId() == null || bill.getId().isEmpty()) {
            bill.setId("bill-" + UUID.randomUUID().toString().substring(0, 8));
        } else if (repo.existsById(bill.getId())) {
            // Saving here would replace an issued bill.
            throw new PriceRuleException(HttpStatus.CONFLICT, "BILL_ALREADY_EXISTS", "A bill with this id already exists.");
        }
        if (bill.getCreatedAt() == null) bill.setCreatedAt(LocalDateTime.now());

        RentQuote quote = rentQuotes.quote(bill.getRoomId(), RentQuoteService.parseBillingMonth(bill.getBillingMonth()));
        bill.setRentAmount(quote.rentAmount());
        bill.setLeaseId(quote.leaseId());
        calculateAmounts(bill);

        if (bill.getStatus() == null) bill.setStatus("Pending");

        UtilityBill saved = repo.save(bill);

        // Update room meters
        if (saved.getRoomId() != null) {
            roomRepo.findById(saved.getRoomId()).ifPresent(room -> {
                room.setPrevWaterMeter(saved.getCurrWaterMeter());
                room.setPrevElectricMeter(saved.getCurrElectricMeter());
                roomRepo.save(room);
            });
        }

        logService.log("Utility Bill Created",
            String.format("Created bill %s for Unit %s (%.0f THB)", saved.getInvoiceNo(), saved.getRoomNumber(), saved.getTotalAmount()));
        return saved;
    }

    @PutMapping("/{id}")
    public ResponseEntity<UtilityBill> update(@PathVariable String id, @RequestBody UtilityBill bill) {
        return repo.findById(id).map(stored -> {
            // An issued bill keeps its month and rent, whatever the room's price has become since.
            bill.setId(id);
            bill.setBillingMonth(stored.getBillingMonth());
            bill.setRentAmount(stored.getRentAmount() != null ? stored.getRentAmount() : rentInsideTotal(stored));
            calculateAmounts(bill);
            return ResponseEntity.ok(repo.save(bill));
        }).orElse(ResponseEntity.notFound().build());
    }

    /** Older bills stored no rent, but their total already includes the rent charged then. */
    private double rentInsideTotal(UtilityBill bill) {
        double total = bill.getTotalAmount() != null ? bill.getTotalAmount() : 0;
        double water = bill.getWaterAmount() != null ? bill.getWaterAmount() : 0;
        double electric = bill.getElectricAmount() != null ? bill.getElectricAmount() : 0;
        double commonFee = bill.getCommonFee() != null ? bill.getCommonFee() : 300;
        return Math.max(0, total - water - electric - commonFee);
    }

    @GetMapping("/rent-quote")
    public RentQuote rentQuote(@RequestParam String roomId, @RequestParam String month) {
        return rentQuotes.quote(roomId, RentQuoteService.parseBillingMonth(month));
    }

    private void calculateAmounts(UtilityBill bill) {
        double waterUnits = Math.max(0, (bill.getCurrWaterMeter() != null ? bill.getCurrWaterMeter() : 0) - (bill.getPrevWaterMeter() != null ? bill.getPrevWaterMeter() : 0));
        double electricUnits = Math.max(0, (bill.getCurrElectricMeter() != null ? bill.getCurrElectricMeter() : 0) - (bill.getPrevElectricMeter() != null ? bill.getPrevElectricMeter() : 0));
        double waterRate = bill.getWaterRate() != null ? bill.getWaterRate() : 9.0;
        double electricRate = bill.getElectricRate() != null ? bill.getElectricRate() : 4.0;

        bill.setWaterAmount(waterUnits * waterRate);
        bill.setElectricAmount(electricUnits * electricRate);

        double rent = bill.getRentAmount() != null ? bill.getRentAmount() : 0;
        double commonFee = bill.getCommonFee() != null ? bill.getCommonFee() : 300;
        bill.setTotalAmount(rent + bill.getWaterAmount() + bill.getElectricAmount() + commonFee);
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<UtilityBill> updateStatus(@PathVariable String id, @RequestBody Map<String, String> req) {
        return repo.findById(id).map(bill -> {
            bill.setStatus(req.get("status"));
            if ("Paid".equals(req.get("status"))) {
                bill.setPaymentDate(java.time.LocalDate.now().toString());
            }
            if (req.containsKey("slipImage")) {
                bill.setSlipImage(req.get("slipImage"));
            }
            logService.log("Bill Status Updated", String.format("Bill %s set to %s", bill.getInvoiceNo(), req.get("status")));
            return ResponseEntity.ok(repo.save(bill));
        }).orElse(ResponseEntity.notFound().build());
    }
}
