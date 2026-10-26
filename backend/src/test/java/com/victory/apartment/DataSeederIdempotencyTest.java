package com.victory.apartment;

import static org.assertj.core.api.Assertions.assertThat;

import com.victory.apartment.config.DataSeeder;
import com.victory.apartment.model.Room;
import com.victory.apartment.repository.RoomRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DataSeederIdempotencyTest {

    @Autowired
    private DataSeeder dataSeeder;

    @Autowired
    private RoomRepository rooms;

    @Test
    void runningSeederAgainDoesNotModifyExistingRoom() {

        String roomId = "test-room-" + UUID.randomUUID();

        Room room = new Room();
        room.setId(roomId);
        room.setRoomNumber("CUSTOM-" + roomId);
        room.setFloor(1);
        room.setRoomName("Custom Room");
        room.setStatus("Available");
        room.setPrice(9999.0);

        rooms.saveAndFlush(room);

        try {
            long countBefore = rooms.count();

            // เรียก Seeder ซ้ำเหมือนเริ่มแอปอีกครั้ง
            dataSeeder.seed();

            Room saved = rooms.findById(roomId).orElseThrow();

            assertThat(saved.getRoomName()).isEqualTo("Custom Room");
            assertThat(saved.getPrice()).isEqualTo(9999.0);
            assertThat(saved.getStatus()).isEqualTo("Available");
            assertThat(rooms.count()).isEqualTo(countBefore);

        } finally {
            rooms.deleteById(roomId);
        }
    }
}
