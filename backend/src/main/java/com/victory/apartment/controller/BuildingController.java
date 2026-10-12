package com.victory.apartment.controller;

import com.victory.apartment.model.Building;
import com.victory.apartment.repository.BuildingRepository;
import com.victory.apartment.service.ActivityLogService;
import org.springframework.http.ResponseEntity;
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

    public BuildingController(BuildingRepository repo, com.victory.apartment.repository.RoomRepository roomRepo, ActivityLogService logService) {
        this.repo = repo;
        this.roomRepo = roomRepo;
        this.logService = logService;
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
    public Building create(@RequestBody Building building) {
        if (building.getId() == null || building.getId().isEmpty()) {
            building.setId("bld-" + UUID.randomUUID().toString().substring(0, 8));
        }
        if (building.getCreatedAt() == null) {
            building.setCreatedAt(LocalDateTime.now());
        }
        int floors = building.getFloors() != null ? building.getFloors() : 2;
        int totalRooms = building.getTotalRooms() != null ? building.getTotalRooms() : 24;
        building.setFloors(floors);
        building.setTotalRooms(totalRooms);
        if (building.getCoverImage() == null || building.getCoverImage().isEmpty()) {
            building.setCoverImage("https://images.unsplash.com/photo-1545324418-cc1a3fa10c00?auto=format&fit=crop&w=800&q=80");
        }

        Building saved = repo.save(building);

        // Auto-generate room records if none exist for this building
        int unitsPerFloor = Math.max(1, totalRooms / Math.max(1, floors));
        String codePrefix = (building.getCode() != null && !building.getCode().isEmpty()) ? building.getCode().trim().toUpperCase() : "R";

        for (int floor = 1; floor <= floors; floor++) {
            for (int unit = 1; unit <= unitsPerFloor; unit++) {
                String roomNum = String.format("%s%d%02d", codePrefix, floor, unit);
                if (roomRepo.findByRoomNumber(roomNum).isEmpty()) {
                    String type;
                    if (unit <= 4) {
                        type = "Studio (Single Bed)";
                    } else if (unit <= 8) {
                        type = "Studio (Double Bed)";
                    } else if (unit <= 11) {
                        type = "1-Bedroom";
                    } else {
                        type = "Corner Room";
                    }
                    com.victory.apartment.model.Room room = new com.victory.apartment.model.Room();
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
                    room.setBuildingId(saved.getId());
                    room.setBuildingName(saved.getName());
                    room.setPrevWaterMeter(100.0);
                    room.setCurrWaterMeter(100.0);
                    room.setPrevElectricMeter(100.0);
                    room.setCurrElectricMeter(100.0);
                    room.setCreatedAt(LocalDateTime.now());
                    roomRepo.save(room);
                }
            }
        }

        logService.log("Building Created", "Created building " + building.getName() + " (" + building.getCode() + ") with " + totalRooms + " units");
        return saved;
    }

    @PutMapping("/{id}")
    public ResponseEntity<Building> update(@PathVariable String id, @RequestBody Building incoming) {
        return repo.findById(id).map(existing -> {
            if (incoming.getName() != null) existing.setName(incoming.getName());
            if (incoming.getCode() != null) existing.setCode(incoming.getCode());
            if (incoming.getFloors() != null) existing.setFloors(incoming.getFloors());
            if (incoming.getTotalRooms() != null) existing.setTotalRooms(incoming.getTotalRooms());
            if (incoming.getDescription() != null) existing.setDescription(incoming.getDescription());
            if (incoming.getCoverImage() != null) existing.setCoverImage(incoming.getCoverImage());
            if (incoming.getAddress() != null) existing.setAddress(incoming.getAddress());

            logService.log("Building Updated", "Updated building " + existing.getName());
            return ResponseEntity.ok(repo.save(existing));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        return repo.findById(id).map(bld -> {
            repo.deleteById(id);
            logService.log("Building Deleted", "Deleted building " + bld.getName() + " (" + id + ")");
            return ResponseEntity.ok().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }
}

