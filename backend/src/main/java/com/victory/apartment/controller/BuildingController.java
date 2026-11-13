package com.victory.apartment.controller;

import com.victory.apartment.model.Building;
import com.victory.apartment.model.Room;
import com.victory.apartment.repository.BuildingRepository;
import com.victory.apartment.repository.LeaseRepository;
import com.victory.apartment.repository.UtilityBillRepository;
import com.victory.apartment.repository.BookingRepository;
import com.victory.apartment.repository.MaintenancePredictionRepository;
import com.victory.apartment.repository.ScheduledReminderRepository;
import com.victory.apartment.repository.RoomPriceRepository;
import com.victory.apartment.repository.RoomRepository;
import com.victory.apartment.service.ActivityLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/buildings")
public class BuildingController {

    private static final Logger log = LoggerFactory.getLogger(BuildingController.class);

    private final BuildingRepository repo;
    private final RoomRepository roomRepo;
    private final ActivityLogService logService;
    private LeaseRepository leaseRepo;
    private UtilityBillRepository billRepo;
    private BookingRepository bookingRepo;
    private MaintenancePredictionRepository predictionRepo;
    private ScheduledReminderRepository reminderRepo;
    private RoomPriceRepository priceRepo;

    public BuildingController(BuildingRepository repo, RoomRepository roomRepo, ActivityLogService logService) {
        this.repo = repo;
        this.roomRepo = roomRepo;
        this.logService = logService;
    }

    @Autowired(required = false)
    public void setLeaseRepository(LeaseRepository leaseRepo) { this.leaseRepo = leaseRepo; }

    @Autowired(required = false)
    public void setUtilityBillRepository(UtilityBillRepository billRepo) { this.billRepo = billRepo; }

    @Autowired(required = false)
    public void setBookingRepository(BookingRepository bookingRepo) { this.bookingRepo = bookingRepo; }

    @Autowired(required = false)
    public void setMaintenancePredictionRepository(MaintenancePredictionRepository predictionRepo) { this.predictionRepo = predictionRepo; }

    @Autowired(required = false)
    public void setScheduledReminderRepository(ScheduledReminderRepository reminderRepo) { this.reminderRepo = reminderRepo; }

    @Autowired(required = false)
    public void setRoomPriceRepository(RoomPriceRepository priceRepo) { this.priceRepo = priceRepo; }

    @GetMapping
    public List<Building> getAll() {
        return repo.findAllByOrderByCodeAsc();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Building> getById(@PathVariable String id) {
        return repo.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> create(@RequestBody Building building) {
        if (building.getName() == null || building.getName().isBlank()) return ResponseEntity.badRequest().body(Map.of("message", "Building name is required."));
        if (building.getCode() == null || building.getCode().isBlank()) {
            String code = building.getName().replaceAll("[^A-Za-z0-9]", "").toUpperCase();
            building.setCode(code.substring(0, Math.min(code.length(), 8)));
            if (building.getCode().isBlank()) building.setCode("BLDG");
        }
        if (building.getFloors() == null) building.setFloors(1);
        if (building.getTotalRooms() == null) building.setTotalRooms(1);
        if (!validConfig(building.getFloors(), building.getTotalRooms())) return ResponseEntity.badRequest().body(Map.of("message", "Floors and room count must be valid positive integers."));
        if (building.getId() == null || building.getId().isEmpty()) {
            building.setId("bld-" + UUID.randomUUID().toString().substring(0, 8));
        }
        if (building.getCreatedAt() == null) {
            building.setCreatedAt(LocalDateTime.now());
        }
        int floors = building.getFloors();
        int totalRooms = building.getTotalRooms();
        building.setFloors(floors);
        building.setTotalRooms(totalRooms);
        if (building.getCoverImage() == null || building.getCoverImage().isEmpty()) {
            building.setCoverImage("https://images.unsplash.com/photo-1545324418-cc1a3fa10c00?auto=format&fit=crop&w=800&q=80");
        }

        Building saved = repo.save(building);

        addRoomsToTarget(building, totalRooms);

        logService.log("Building Created", "Created building " + building.getName() + " (" + building.getCode() + ") with " + totalRooms + " units");
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> update(@PathVariable String id, @RequestBody Building incoming) {
        try {
            return repo.findById(id).map(existing -> {
                int floors = incoming.getFloors() != null ? incoming.getFloors() : (existing.getFloors() != null ? existing.getFloors() : 1);
                int totalRooms = incoming.getTotalRooms() != null ? incoming.getTotalRooms() : (existing.getTotalRooms() != null ? existing.getTotalRooms() : 1);
                if (!validConfig(floors, totalRooms)) return ResponseEntity.badRequest().body(Map.of("message", "Floors and room count must be positive integers."));
                List<Room> rooms = roomRepo.findByBuildingId(id);
                if (rooms.isEmpty()) rooms = roomRepo.findAll().stream().filter(r -> existing.getCode().equalsIgnoreCase(buildingCodeFromRoom(r.getRoomNumber()))).toList();
                List<Room> removable = rooms.stream().filter(this::isRoomSafeToRemove).toList();
                int mustRemove = Math.max(0, rooms.size() - totalRooms);
                long outOfRange = rooms.stream().filter(r -> r.getFloor() > floors).count();
                if (mustRemove > removable.size() || outOfRange > removable.stream().filter(r -> r.getFloor() > floors).count()) {
                    return ResponseEntity.badRequest().body(Map.of("message", "Cannot reduce floors or room count while affected rooms have tenants, leases, bookings, or billing records."));
                }
                List<Room> toRemove = rooms.stream().filter(r -> r.getFloor() > floors && isRoomSafeToRemove(r))
                        .sorted(Comparator.comparing(Room::getRoomNumber).reversed()).toList();
                Set<String> removeIds = new HashSet<>();
                toRemove.forEach(r -> removeIds.add(r.getId()));
                int remainingToRemove = mustRemove - removeIds.size();
                rooms.stream().filter(this::isRoomSafeToRemove).filter(r -> !removeIds.contains(r.getId()))
                        .sorted(Comparator.comparing(Room::getRoomNumber).reversed()).limit(Math.max(0, remainingToRemove))
                        .forEach(r -> removeIds.add(r.getId()));

                List<Room> roomsToDelete = rooms.stream().filter(r -> removeIds.contains(r.getId())).toList();
                deleteRoomsSafely(roomsToDelete);

                if (incoming.getName() != null) existing.setName(incoming.getName());
                if (incoming.getCode() != null) existing.setCode(incoming.getCode());
                existing.setFloors(floors);
                existing.setTotalRooms(totalRooms);
                if (incoming.getDescription() != null) existing.setDescription(incoming.getDescription());
                if (incoming.getCoverImage() != null) existing.setCoverImage(incoming.getCoverImage());
                if (incoming.getAddress() != null) existing.setAddress(incoming.getAddress());

                logService.log("Building Updated", "Updated building " + existing.getName());
                Building saved = repo.save(existing);
                addRoomsToTarget(existing, totalRooms);
                return ResponseEntity.ok(saved);
            }).orElse(ResponseEntity.notFound().build());
        } catch (Exception ex) {
            log.error("Failed to update building with ID {}: {}", id, ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Cannot update building: " + (ex.getMessage() != null ? ex.getMessage() : "Internal error")));
        }
    }

    private void deleteRoomsSafely(List<Room> roomsToDelete) {
        if (roomsToDelete == null || roomsToDelete.isEmpty()) return;
        List<String> roomIds = roomsToDelete.stream().map(Room::getId).filter(Objects::nonNull).toList();

        try {
            if (predictionRepo != null && !roomIds.isEmpty()) {
                predictionRepo.deleteByRoomIdIn(roomIds);
            }
        } catch (Exception ex) {
            log.warn("Error cleaning up predictions for rooms: {}", ex.getMessage());
        }

        try {
            if (reminderRepo != null && !roomIds.isEmpty()) {
                reminderRepo.deleteByRoomIdIn(roomIds);
            }
        } catch (Exception ex) {
            log.warn("Error cleaning up reminders for rooms: {}", ex.getMessage());
        }

        try {
            if (priceRepo != null && !roomIds.isEmpty()) {
                priceRepo.deleteByRoomIdIn(roomIds);
            }
        } catch (Exception ex) {
            log.warn("Error cleaning up prices for rooms: {}", ex.getMessage());
        }

        roomRepo.deleteAll(roomsToDelete);
        roomRepo.flush();
    }

    private boolean validConfig(Integer floors, Integer totalRooms) {
        return floors != null && floors > 0 && totalRooms != null && totalRooms > 0 && floors <= totalRooms && totalRooms <= 5000;
    }

    private boolean isRoomSafeToRemove(Room room) {
        if (!"Available".equalsIgnoreCase(room.getStatus()) || room.getCurrentTenantId() != null) return false;
        if (leaseRepo != null && !leaseRepo.findByRoomIdAndStatusIn(room.getId(), List.of("Active", "Expired", "Terminated")).isEmpty()) return false;
        if (bookingRepo != null && !bookingRepo.findByRoomIdAndStatusIn(room.getId(), List.of("Pending", "Approved")).isEmpty()) return false;
        if (billRepo != null && billRepo.existsByRoomId(room.getId())) return false;
        return true;
    }

    private void addRoomsToTarget(Building building, int target) {
        List<Room> existing = roomRepo.findByBuildingId(building.getId());
        if (existing.isEmpty()) {
            existing = roomRepo.findAll().stream().filter(r -> building.getCode().equalsIgnoreCase(buildingCodeFromRoom(r.getRoomNumber()))).toList();
        }
        int missing = target - existing.size();
        if (missing <= 0) return;
        int[] counts = new int[building.getFloors() + 1];
        for (Room room : existing) if (room.getFloor() != null && room.getFloor() <= building.getFloors()) counts[room.getFloor()]++;
        String code = building.getCode().trim().toUpperCase();
        for (int i = 0; i < missing; i++) {
            int floor = 1;
            for (int f = 2; f <= building.getFloors(); f++) if (counts[f] < counts[floor]) floor = f;
            int unit = 1;
            String roomNum;
            do { roomNum = String.format("%s%d%02d", code, floor, unit++); } while (roomRepo.findByRoomNumber(roomNum).isPresent());
            createRoom(building, roomNum, floor, unit - 1);
            counts[floor]++;
        }
    }

    private String buildingCodeFromRoom(String roomNumber) {
        if (roomNumber == null || roomNumber.length() < 2) return "";
        int firstDigit = -1;
        for (int i = 0; i < roomNumber.length(); i++) {
            if (Character.isDigit(roomNumber.charAt(i))) {
                firstDigit = i;
                break;
            }
        }
        if (firstDigit <= 0) return "";
        return roomNumber.substring(0, firstDigit);
    }

    private void createRoom(Building building, String roomNum, int floor, int unit) {
        String type = unit <= 4 ? "Studio (Single Bed)" : unit <= 8 ? "Studio (Double Bed)" : unit <= 11 ? "1-Bedroom" : "Corner Room";
        Room room = new Room();
        room.setId("rm-" + UUID.randomUUID().toString().substring(0, 8));
        room.setRoomNumber(roomNum);
        room.setFloor(floor);
        room.setRoomName(String.format("Unit %s (%s)", roomNum, type));
        room.setRoomType(type);
        room.setDescription(String.format("ห้องพักชั้น %d %s ตกแต่งครบครัน เครื่องปรับอากาศ เครื่องทำน้ำอุ่น อินเทอร์เน็ตความเร็วสูง", floor, type));
        room.setCapacity(2);
        room.setPrice(5500.0 + (floor - 1) * 200.0);
        room.setStatus("Available");
        room.setAmenities("[\"High-Speed Wi-Fi\",\"Air Conditioner\",\"Water Heater\",\"Private Balcony\",\"Digital Keycard\"]");
        room.setSizeSqm(28.0 + (unit % 4) * 2.0);
        room.setBedType("Queen Bed (2 Guests)");
        room.setCoverImage("/rooms/std_1.jpg");
        room.setGallery("[\"/rooms/std_1.jpg\",\"/rooms/std_2.jpg\",\"/rooms/std_3.jpg\"]");
        room.setBuildingId(building.getId());
        room.setBuildingName(building.getName());
        room.setPrevWaterMeter(100.0); room.setCurrWaterMeter(100.0);
        room.setPrevElectricMeter(100.0); room.setCurrElectricMeter(100.0);
        room.setCreatedAt(LocalDateTime.now());
        roomRepo.save(room);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> delete(@PathVariable String id) {
        try {
            return repo.findById(id).map(bld -> {
                // Cascade delete all rooms belonging to this building first
                List<Room> buildingRooms = roomRepo.findByBuildingId(id);
                if (!buildingRooms.isEmpty()) {
                    deleteRoomsSafely(buildingRooms);
                }
                repo.deleteById(id);
                logService.log("Building Deleted", "Deleted building " + bld.getName() + " (" + id + ") with " + buildingRooms.size() + " rooms");
                return ResponseEntity.ok().<Void>build();
            }).orElse(ResponseEntity.notFound().build());
        } catch (Exception ex) {
            log.error("Failed to delete building with ID {}: {}", id, ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Cannot delete building: " + (ex.getMessage() != null ? ex.getMessage() : "Internal error")));
        }
    }
}
