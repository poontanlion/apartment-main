package com.app.config;

import com.app.model.Building;
import com.app.model.Room;
import com.app.repository.BuildingRepository;
import com.app.repository.RoomRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
public class DataSeeder {

    private final BuildingRepository buildingRepo;
    private final RoomRepository roomRepo;

    public DataSeeder(BuildingRepository buildingRepo, RoomRepository roomRepo) {
        this.buildingRepo = buildingRepo;
        this.roomRepo = roomRepo;
    }

    @PostConstruct
    public void seed() {
        seedBuildings();
        seedRooms();
    }

    private void seedBuildings() {
        if (buildingRepo.count() == 0) {
            Building b1 = new Building(
                "bld-1",
                "อาคาร A (Victory Tower A)",
                "A",
                2,
                24,
                "อาคารพักอาศัย 2 ชั้น พร้อมลิฟต์และระบบรักษาความปลอดภัย เดินทางสะดวกติดถนนใหญ่",
                "https://images.unsplash.com/photo-1545324418-cc1a3fa10c00?auto=format&fit=crop&w=800&q=80",
                "123/1 ถนนสุขุมวิท กรุงเทพฯ",
                LocalDateTime.of(2026, 1, 1, 0, 0)
            );
            Building b2 = new Building(
                "bld-2",
                "อาคาร B (Victory Residence B)",
                "B",
                2,
                12,
                "อาคารพักอาศัย 2 ชั้น สไตล์โมเดิร์น บรรยากาศเงียบสงบพร้อมสวนส่วนกลาง",
                "https://images.unsplash.com/photo-1577495508048-b635879837f1?auto=format&fit=crop&w=800&q=80",
                "123/2 ถนนสุขุมวิท กรุงเทพฯ",
                LocalDateTime.of(2026, 2, 15, 0, 0)
            );
            buildingRepo.save(b1);
            buildingRepo.save(b2);
        }
    }

    private void seedRooms() {
        if (roomRepo.count() > 0) {
            return;
        }

        Map<String, String[]> assigned = new HashMap<>();
        assigned.put("A101", new String[]{"Somchai Jaidee", "t-101", "Occupied", "5500", "Studio (Single Bed)"});
        assigned.put("A102", new String[]{"Malee Rattanaporn", "t-102", "Occupied", "5500", "Studio (Single Bed)"});
        assigned.put("A105", new String[]{"-", "", "Maintenance", "5500", "Studio (Double Bed)"});
        assigned.put("A108", new String[]{"David Miller", "t-108", "Occupied", "6000", "Studio (Double Bed)"});
        assigned.put("A201", new String[]{"Wichai Sirisuk", "t-201", "Occupied", "6200", "Studio (Single Bed)"});
        assigned.put("A204", new String[]{"Anan Suksawat", "t-204", "Occupied", "7500", "1-Bedroom"});
        assigned.put("A207", new String[]{"Napa Charoenwong", "t-207", "Reserved", "6500", "Studio (Double Bed)"});
        assigned.put("A210", new String[]{"Kittisak Meechai", "t-210", "Occupied", "8500", "Corner Room"});

        assigned.put("B101", new String[]{"Ploy Srivilai", "t-301", "Occupied", "5800", "Studio (Single Bed)"});
        assigned.put("B103", new String[]{"Alex Turner", "t-303", "Occupied", "6200", "Studio (Double Bed)"});
        assigned.put("B202", new String[]{"Chaiwat Boonmee", "t-304", "Occupied", "6500", "Studio (Double Bed)"});
        assigned.put("B205", new String[]{"-", "", "Reserved", "8000", "1-Bedroom"});

        // Seed Building A (2 floors, 12 rooms each = 24 rooms)
        for (int floor = 1; floor <= 2; floor++) {
            for (int i = 1; i <= 12; i++) {
                String roomNum = String.format("A%d%02d", floor, i);
                createAndSaveRoom(roomNum, floor, i, "bld-1", "อาคาร A (Victory Tower A)", assigned.get(roomNum));
            }
        }

        // Seed Building B (2 floors, 6 rooms each = 12 rooms)
        for (int floor = 1; floor <= 2; floor++) {
            for (int i = 1; i <= 6; i++) {
                String roomNum = String.format("B%d%02d", floor, i);
                createAndSaveRoom(roomNum, floor, i, "bld-2", "อาคาร B (Victory Residence B)", assigned.get(roomNum));
            }
        }
    }

    private void createAndSaveRoom(String roomNum, int floor, int i, String bldId, String bldName, String[] a) {
        String type;
        double rent;
        double size;
        int capacity;
        String bedType;
        String coverImage = "https://images.unsplash.com/photo-1522708323590-d24dbb6b0267?auto=format&fit=crop&w=800&q=80";
        String gallery = "[]";
        String amenities;

        if (i <= 4) {
            type = "Studio (Single Bed)";
            rent = 5500.0;
            size = 26.0 + (i % 2) * 2;
            capacity = (i % 2 == 0) ? 1 : 2;
            bedType = (i % 2 == 0) ? "Single Bed (1 Guest)" : "Queen Bed (2 Guests)";
            amenities = "[\"High-Speed Wi-Fi\",\"Air Conditioner\",\"Water Heater\",\"Private Balcony\",\"Keycard Access\",\"Work Desk\"]";
        } else if (i <= 8) {
            type = "Studio (Double Bed)";
            rent = (floor == 1) ? 6000.0 : 6500.0;
            size = 32.0 + (i % 2) * 3;
            capacity = 2;
            bedType = (i % 2 == 0) ? "King Bed (2 Guests)" : "Super King Bed (2 Guests)";
            amenities = "[\"High-Speed Wi-Fi\",\"Air Conditioner\",\"Water Heater\",\"Private Balcony\",\"Keycard Access\",\"Work Desk & Ergonomic Chair\",\"Smart TV 43\\\"\",\"Refrigerator 6.5 cu.ft\"]";
        } else if (i <= 11) {
            type = "1-Bedroom";
            rent = 7500.0;
            size = 42.0;
            capacity = 3;
            bedType = "King Bed + Sofa Bed (3 Guests)";
            amenities = "[\"High-Speed Wi-Fi\",\"Air Conditioner\",\"Water Heater\",\"Private Balcony\",\"Keycard Access\",\"Separate Living Area\",\"Kitchenette & Microwave\",\"Smart TV 50\\\"\",\"Washing Machine\"]";
        } else {
            type = "Corner Room";
            rent = 8500.0;
            size = 50.0;
            capacity = 4;
            bedType = "King Bed + Twin Beds (4 Guests)";
            amenities = "[\"High-Speed Wi-Fi\",\"Air Conditioner\",\"Water Heater\",\"Private Balcony\",\"Keycard Access\",\"Panoramic Windows\",\"Corner Balcony\",\"Dining Table\",\"Smart TV 55\\\"\",\"Full Kitchen\"]";
        }

        Room r = new Room();
        r.setId("rm-" + roomNum.toLowerCase());
        r.setRoomNumber(roomNum);
        r.setFloor(floor);
        r.setRoomType(type);
        r.setRoomName(String.format("Unit %s (%s)", roomNum, type));
        r.setDescription(String.format("ห้องพักชั้น %d %s พร้อมระเบียงส่วนตัว ตกแต่งด้วยเฟอร์นิเจอร์คุณภาพครบครัน เครื่องปรับอากาศอินเวอร์เตอร์ เครื่องทำน้ำอุ่น และอินเทอร์เน็ต Wi-Fi ความเร็วสูง", floor, type));
        r.setCapacity(capacity);
        r.setPrice(rent);
        r.setSizeSqm(size);
        r.setBedType(bedType);
        r.setCoverImage(coverImage);
        r.setGallery(gallery);
        r.setAmenities(amenities);
        r.setBuildingId(bldId);
        r.setBuildingName(bldName);
        r.setPrevWaterMeter(100.0 + (i * 5));
        r.setCurrWaterMeter(105.0 + (i * 5));
        r.setPrevElectricMeter(200.0 + (i * 15));
        r.setCurrElectricMeter(225.0 + (i * 15));
        r.setCreatedAt(LocalDateTime.now().minusDays(30));

        if (a != null) {
            r.setCurrentTenantName(a[0]);
            r.setCurrentTenantId(a[1]);
            r.setStatus(a[2]);
            try {
                r.setPrice(Double.parseDouble(a[3]));
            } catch (Exception ignored) {}
            if (a.length > 4 && !a[4].isEmpty()) {
                r.setRoomType(a[4]);
            }
        } else {
            r.setStatus("Available");
            r.setCurrentTenantId(null);
            r.setCurrentTenantName(null);
        }

        roomRepo.save(r);
    }
}
