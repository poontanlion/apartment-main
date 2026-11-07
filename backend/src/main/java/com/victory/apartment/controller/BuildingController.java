package com.victory.apartment.controller;

import com.victory.apartment.model.Building;
import com.victory.apartment.repository.BuildingRepository;
import com.victory.apartment.repository.LeaseRepository;
import com.victory.apartment.repository.UtilityBillRepository;
import com.victory.apartment.repository.BookingRepository;
import com.victory.apartment.model.Room;
import com.victory.apartment.service.ActivityLogService;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/buildings")
public class BuildingController {

    private final BuildingRepository repo;
    private final com.victory.apartment.repository.RoomRepository roomRepo;
    private final ActivityLogService logService;
    private final LeaseRepository leaseRepo;
    private final UtilityBillRepository billRepo;
    private final BookingRepository bookingRepo;

    public BuildingController(BuildingRepository repo, com.victory.apartment.repository.RoomRepository roomRepo, ActivityLogService logService,
                              LeaseRepository leaseRepo, UtilityBillRepository billRepo, BookingRepository bookingRepo) {
        this.repo = repo;
        this.roomRepo = roomRepo;
        this.logService = logService;
        this.leaseRepo = leaseRepo;
        this.billRepo = billRepo;
        this.bookingRepo = bookingRepo;
    }

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
        if (building.getName() == null || building.getName().isBlank() || building.getCode() == null || building.getCode().isBlank() || !validConfig(building.getFloors(), building.getTotalRooms())) return ResponseEntity.badRequest().body(java.util.Map.of("message", "Building name, code, floors, and room count are required and must be valid."));
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

        addRoomsToTarget(saved, totalRooms);

        logService.log("Building Created", "Created building " + building.getName() + " (" + building.getCode() + ") with " + totalRooms + " units");
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> update(@PathVariable String id, @RequestBody Building incoming) {
        return repo.findById(id).map(existing -> {
            int floors = incoming.getFloors() != null ? incoming.getFloors() : existing.getFloors();
            int totalRooms = incoming.getTotalRooms() != null ? incoming.getTotalRooms() : existing.getTotalRooms();
            if (!validConfig(floors, totalRooms)) return ResponseEntity.badRequest().body(java.util.Map.of("message", "Floors and room count must be positive integers."));
            List<Room> rooms = roomRepo.findByBuildingId(id);
            if (rooms.isEmpty()) rooms = roomRepo.findAll().stream().filter(r -> existing.getCode().equalsIgnoreCase(buildingCodeFromRoom(r.getRoomNumber()))).toList();
            List<Room> removable = rooms.stream().filter(this::isRoomSafeToRemove).toList();
            int mustRemove = Math.max(0, rooms.size() - totalRooms);
            long outOfRange = rooms.stream().filter(r -> r.getFloor() > floors).count();
            if (mustRemove > removable.size() || outOfRange > removable.stream().filter(r -> r.getFloor() > floors).count()) {
                return ResponseEntity.badRequest().body(java.util.Map.of("message", "Cannot reduce floors or room count while affected rooms have tenants, leases, bookings, or billing records."));
            }
            List<Room> toRemove = rooms.stream().filter(r -> r.getFloor() > floors && isRoomSafeToRemove(r))
                    .sorted(java.util.Comparator.comparing(Room::getRoomNumber).reversed()).toList();
            java.util.Set<String> removeIds = new java.util.HashSet<>();
            toRemove.forEach(r -> removeIds.add(r.getId()));
            int remainingToRemove = mustRemove - removeIds.size();
            rooms.stream().filter(this::isRoomSafeToRemove).filter(r -> !removeIds.contains(r.getId()))
                    .sorted(java.util.Comparator.comparing(Room::getRoomNumber).reversed()).limit(Math.max(0, remainingToRemove))
                    .forEach(r -> removeIds.add(r.getId()));
            roomRepo.deleteAll(rooms.stream().filter(r -> removeIds.contains(r.getId())).toList());
            if (incoming.getName() != null) existing.setName(incoming.getName());
            if (incoming.getCode() != null) existing.setCode(incoming.getCode());
            existing.setFloors(floors);
            existing.setTotalRooms(totalRooms);
            if (incoming.getDescription() != null) existing.setDescription(incoming.getDescription());
            if (incoming.getCoverImage() != null) existing.setCoverImage(incoming.getCoverImage());
            if (incoming.getAddress() != null) existing.setAddress(incoming.getAddress());

            logService.log("Building Updated", "Updated building " + existing.getName());
            Building saved = repo.save(existing);
            addRoomsToTarget(saved, totalRooms);
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    private boolean validConfig(Integer floors, Integer totalRooms) {
        return floors != null && floors > 0 && totalRooms != null && totalRooms > 0 && floors <= totalRooms && totalRooms <= 5000;
    }

    private boolean isRoomSafeToRemove(Room room) {
        if (!"Available".equalsIgnoreCase(room.getStatus()) || room.getCurrentTenantId() != null) return false;
        if (!leaseRepo.findByRoomIdAndStatusIn(room.getId(), List.of("Active", "Expired", "Terminated")).isEmpty()) return false;
        if (!bookingRepo.findByRoomIdAndStatusIn(room.getId(), List.of("Pending", "Approved")).isEmpty()) return false;
        return billRepo.findAll().stream().noneMatch(b -> room.getId().equals(b.getRoomId()));
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
        return roomNumber.substring(0, roomNumber.length() - 3);
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
    public ResponseEntity<Void> delete(@PathVariable String id) {
        return repo.findById(id).map(bld -> {
            // Cascade delete all rooms belonging to this building first
            java.util.List<com.victory.apartment.model.Room> buildingRooms = roomRepo.findByBuildingId(id);
            if (!buildingRooms.isEmpty()) {
                roomRepo.deleteAll(buildingRooms);
            }
            repo.deleteById(id);
            logService.log("Building Deleted", "Deleted building " + bld.getName() + " (" + id + ") with " + buildingRooms.size() + " rooms");
            return ResponseEntity.ok().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }
}

