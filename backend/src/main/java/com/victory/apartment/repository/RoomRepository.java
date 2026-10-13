package com.victory.apartment.repository;

import com.victory.apartment.model.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RoomRepository extends JpaRepository<Room, String> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from Room r where r.id = :id")
    java.util.Optional<Room> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") String id);
    List<Room> findAllByOrderByRoomNumberAsc();
    List<Room> findByBuildingId(String buildingId);
    List<Room> findByBuildingIdOrderByRoomNumberAsc(String buildingId);
    List<Room> findByStatus(String status);
    List<Room> findByFloor(Integer floor);
    java.util.Optional<Room> findByRoomNumber(String roomNumber);
}

