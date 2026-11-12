package com.victory.apartment.config;

import com.victory.apartment.service.RoomPriceService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Applies scheduled prices to Room.price when their month arrives, and on startup to catch up after downtime. */
@Component
public class RoomPriceScheduler {

    private final RoomPriceService service;

    public RoomPriceScheduler(RoomPriceService service) {
        this.service = service;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        service.syncCurrentPrices();
    }

    @Scheduled(cron = "0 5 0 1 * *", zone = "Asia/Bangkok")
    public void syncAtMonthStart() {
        service.syncCurrentPrices();
    }
}
