package com.victory.apartment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
@EnableScheduling
public class PricingConfig {

    /** Billing months follow Bangkok time even when the container runs in UTC. */
    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Bangkok"));
    }
}
