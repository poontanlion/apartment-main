package com.victory.apartment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.victory.apartment.TestcontainersConfiguration;
import com.victory.apartment.model.Room;
import com.victory.apartment.model.RoomPrice;
import com.victory.apartment.repository.RoomPriceRepository;
import com.victory.apartment.repository.RoomRepository;
import com.victory.apartment.service.RoomPriceService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;

import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RoomPriceIntegrationTest {

    @Autowired private RoomPriceService service;
    @Autowired private RoomPriceRepository prices;
    @Autowired private RoomRepository rooms;
    @Autowired private List<ScheduledTaskHolder> scheduledTaskHolders;
    @Autowired private ConfigurableApplicationContext context;

    private Room room;

    @BeforeEach
    void createRoom() {
        String suffix = UUID.randomUUID().toString();
        room = new Room();
        room.setId("price-room-" + suffix);
        room.setRoomNumber("PRICE-" + suffix);
        room.setFloor(1);
        room.setPrice(6000.0);
        rooms.save(room);
    }

    private RoomPrice row(LocalDate month, double price) {
        RoomPrice r = new RoomPrice();
        r.setId("rp-" + UUID.randomUUID());
        r.setRoomId(room.getId());
        r.setEffectiveMonth(month);
        r.setPrice(price);
        return r;
    }

    @Test
    void uniqueRoomAndMonth() {
        LocalDate month = LocalDate.of(2030, 1, 1);
        prices.saveAndFlush(row(month, 6500));

        assertThatThrownBy(() -> prices.saveAndFlush(row(month, 6700)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingRoomRemovesItsPrices() {
        service.setPrice(room.getId(), 6500.0, service.currentMonth().plusMonths(1));
        assertThat(prices.findByRoomIdOrderByEffectiveMonthDesc(room.getId())).hasSize(2);

        rooms.deleteById(room.getId());

        assertThat(prices.findByRoomIdOrderByEffectiveMonthDesc(room.getId())).isEmpty();
    }

    @Test
    void priceFor_readsLatestRowNotAfterMonthFromDatabase() {
        YearMonth next = service.currentMonth().plusMonths(1);
        service.setPrice(room.getId(), 6500.0, next);

        assertThat(service.priceFor(room, next.minusMonths(1))).isEqualTo(6000.0);
        assertThat(service.priceFor(room, next)).isEqualTo(6500.0);
        assertThat(service.priceFor(room, next.plusMonths(1))).isEqualTo(6500.0);
    }

    @Test
    void applicationStartAppliesPriceThatHasArrived() {
        prices.save(row(service.currentMonth().atDay(1), 7777));
        assertThat(rooms.findById(room.getId()).orElseThrow().getPrice()).isEqualTo(6000.0);

        context.publishEvent(new ApplicationReadyEvent(new SpringApplication(), new String[0], context, Duration.ZERO));

        assertThat(rooms.findById(room.getId()).orElseThrow().getPrice()).isEqualTo(7777.0);
    }

    @Test
    void monthlySyncIsScheduledAtFiveMinutesPastMidnightOnTheFirst() {
        assertThat(scheduledTaskHolders)
            .flatExtracting(ScheduledTaskHolder::getScheduledTasks)
            .extracting(scheduled -> scheduled.getTask())
            .filteredOn(CronTask.class::isInstance)
            .map(CronTask.class::cast)
            .extracting(CronTask::getExpression)
            .contains("0 5 0 1 * *");
    }
}
