package com.victory.apartment.controller;

import com.victory.apartment.model.Room;
import com.victory.apartment.model.RoomPrice;
import com.victory.apartment.repository.RoomRepository;
import com.victory.apartment.service.ActivityLogService;
import com.victory.apartment.service.PriceRuleException;
import com.victory.apartment.service.RoomPriceService;
import io.micrometer.tracing.annotation.NewSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private static final Logger log = LoggerFactory.getLogger(RoomController.class);

    private final RoomRepository repo;
    private final ActivityLogService logService;
    private final RoomPriceService priceService;

    public RoomController(RoomRepository repo, ActivityLogService logService, RoomPriceService priceService) {
        this.repo = repo;
        this.logService = logService;
        this.priceService = priceService;
    }

    @GetMapping
    @NewSpan("getRoomsControllerSpan")
    public List<Room> getAll(@RequestParam(required = false) String buildingId) {
        log.info("Fetching rooms with buildingId filter: {}", buildingId);
        if (buildingId != null && !buildingId.isEmpty() && !"All".equalsIgnoreCase(buildingId)) {
            return repo.findByBuildingIdOrderByRoomNumberAsc(buildingId);
        }
        return repo.findAllByOrderByRoomNumberAsc();
    }

    @GetMapping("/{id}")
    @NewSpan("getRoomByIdControllerSpan")
    public ResponseEntity<Room> getById(@PathVariable String id) {
        log.info("Fetching room by id: {}", id);
        return repo.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Room create(@RequestBody Room room) {
        if (room.getId() == null || room.getId().isEmpty()) {
            room.setId("rm-" + UUID.randomUUID().toString().substring(0, 8));
        } else if (repo.existsById(room.getId())) {
            // Saving here would overwrite the room's price without any history.
            throw new PriceRuleException(HttpStatus.CONFLICT, "ROOM_ALREADY_EXISTS", "A room with this id already exists.");
        }
        if (room.getCreatedAt() == null) room.setCreatedAt(LocalDateTime.now());
        if (room.getStatus() == null || room.getStatus().isEmpty()) room.setStatus("Available");
        if (room.getPrice() == null) room.setPrice(5000.0);
        if (room.getCapacity() == null) room.setCapacity(2);
        if (room.getSizeSqm() == null) room.setSizeSqm(28.0);
        if (room.getAmenities() == null) room.setAmenities("[]");
        if (room.getGallery() == null) room.setGallery("[]");
        if (room.getExtraChargeAmount() != null) {
            applyExtraCharge(room, room.getExtraChargeAmount(), room.getExtraChargeLabel());
        }
        if (room.getPrevWaterMeter() == null) room.setPrevWaterMeter(0.0);
        if (room.getCurrWaterMeter() == null) room.setCurrWaterMeter(0.0);
        if (room.getPrevElectricMeter() == null) room.setPrevElectricMeter(0.0);
        if (room.getCurrElectricMeter() == null) room.setCurrElectricMeter(0.0);

        if (room.getFloor() == null) {
            int floorVal = 1;
            if (room.getRoomNumber() != null) {
                String clean = room.getRoomNumber().replaceAll("^[^0-9]+", "");
                if (!clean.isEmpty()) {
                    if (clean.length() >= 3) {
                        try {
                            floorVal = Integer.parseInt(clean.substring(0, clean.length() - 2));
                        } catch (Exception e) {
                            floorVal = Character.getNumericValue(clean.charAt(0));
                        }
                    } else {
                        floorVal = Character.getNumericValue(clean.charAt(0));
                    }
                }
            }
            room.setFloor(Math.max(1, floorVal));
        }

        logService.log("Room Created", "Created unit " + room.getRoomNumber());
        return repo.save(room);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Room> update(@PathVariable String id, @RequestBody Room incoming) {
        return repo.findById(id).map(existing -> {
            // First, so a rejected price saves nothing. Clients resend the unchanged price on every edit.
            if (incoming.getPrice() != null && !incoming.getPrice().equals(existing.getPrice())) {
                priceService.setPrice(id, incoming.getPrice(), priceService.currentMonth());
                existing.setPrice(incoming.getPrice());
            }
            if (incoming.getRoomNumber() != null) existing.setRoomNumber(incoming.getRoomNumber());
            if (incoming.getFloor() != null) existing.setFloor(incoming.getFloor());
            if (incoming.getRoomName() != null) existing.setRoomName(incoming.getRoomName());
            if (incoming.getRoomType() != null) existing.setRoomType(incoming.getRoomType());
            if (incoming.getDescription() != null) existing.setDescription(incoming.getDescription());
            if (incoming.getCapacity() != null) existing.setCapacity(incoming.getCapacity());
            if (incoming.getStatus() != null) existing.setStatus(incoming.getStatus());
            if (incoming.getAmenities() != null) existing.setAmenities(incoming.getAmenities());
            if (incoming.getSizeSqm() != null) existing.setSizeSqm(incoming.getSizeSqm());
            if (incoming.getBedType() != null) existing.setBedType(incoming.getBedType());
            if (incoming.getCoverImage() != null) existing.setCoverImage(incoming.getCoverImage());
            if (incoming.getGallery() != null) existing.setGallery(incoming.getGallery());
            if (incoming.getCurrentTenantId() != null) existing.setCurrentTenantId(incoming.getCurrentTenantId());
            if (incoming.getCurrentTenantName() != null) existing.setCurrentTenantName(incoming.getCurrentTenantName());
            if (incoming.getBuildingId() != null) existing.setBuildingId(incoming.getBuildingId());
            if (incoming.getBuildingName() != null) existing.setBuildingName(incoming.getBuildingName());
            // Zero clears the charge; the field can't be nulled because only non-null fields are applied.
            if (incoming.getExtraChargeAmount() != null) {
                applyExtraCharge(existing, incoming.getExtraChargeAmount(), incoming.getExtraChargeLabel());
            }
            if (incoming.getPrevWaterMeter() != null) existing.setPrevWaterMeter(incoming.getPrevWaterMeter());
            if (incoming.getCurrWaterMeter() != null) existing.setCurrWaterMeter(incoming.getCurrWaterMeter());
            if (incoming.getPrevElectricMeter() != null) existing.setPrevElectricMeter(incoming.getPrevElectricMeter());
            if (incoming.getCurrElectricMeter() != null) existing.setCurrElectricMeter(incoming.getCurrElectricMeter());

            logService.log("Room Updated", "Updated unit " + existing.getRoomNumber() + " status: " + existing.getStatus());
            return ResponseEntity.ok(repo.save(existing));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        if (!repo.existsById(id)) return ResponseEntity.notFound().build();
        repo.deleteById(id);
        logService.log("Room Deleted", "Deleted unit ID " + id);
        return ResponseEntity.ok().build();
    }

    public record PriceHistory(String currentMonth, List<RoomPrice> prices) {}
    public record PriceRequest(Double price, String effectiveMonth) {}
    public record MonthPrice(String month, Double price) {}

    @GetMapping("/{id}/prices")
    public PriceHistory getPrices(@PathVariable String id) {
        Room room = findRoom(id);
        return new PriceHistory(priceService.currentMonth().toString(), priceService.history(room));
    }

    @PostMapping("/{id}/prices")
    public RoomPrice addPrice(@PathVariable String id, @RequestBody PriceRequest request) {
        return priceService.setPrice(id, request.price(), parseMonth(request.effectiveMonth()));
    }

    @DeleteMapping("/{id}/prices/{priceId}")
    public ResponseEntity<Void> deletePrice(@PathVariable String id, @PathVariable String priceId) {
        priceService.deleteScheduled(id, priceId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/price")
    public MonthPrice getPriceForMonth(@PathVariable String id, @RequestParam String month) {
        YearMonth parsed = parseMonth(month);
        return new MonthPrice(parsed.toString(), priceService.priceFor(findRoom(id), parsed));
    }

    private static final int EXTRA_CHARGE_LABEL_MAX = 100;
    private static final String DEFAULT_EXTRA_CHARGE_LABEL = "Other charges";

    /** An amount of zero means no extra charge; a positive amount needs a label (defaulted when blank). */
    private static void applyExtraCharge(Room room, double amount, String label) {
        if (Double.isNaN(amount) || Double.isInfinite(amount) || amount < 0) {
            throw new PriceRuleException(HttpStatus.BAD_REQUEST, "INVALID_EXTRA_CHARGE", "Extra charge must be zero or more.");
        }
        if (amount == 0) {
            room.setExtraChargeAmount(null);
            room.setExtraChargeLabel(null);
            return;
        }
        String clean = label == null ? "" : label.trim();
        if (clean.length() > EXTRA_CHARGE_LABEL_MAX) {
            throw new PriceRuleException(HttpStatus.BAD_REQUEST, "INVALID_EXTRA_CHARGE_LABEL",
                "Extra charge name must be at most " + EXTRA_CHARGE_LABEL_MAX + " characters.");
        }
        room.setExtraChargeAmount(Math.round(amount * 100.0) / 100.0);
        room.setExtraChargeLabel(clean.isEmpty() ? DEFAULT_EXTRA_CHARGE_LABEL : clean);
    }

    private Room findRoom(String id) {
        return repo.findById(id)
            .orElseThrow(() -> new PriceRuleException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "Room not found."));
    }

    private YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new PriceRuleException(HttpStatus.BAD_REQUEST, "INVALID_MONTH", "Month must be in YYYY-MM format.");
        }
    }
}

