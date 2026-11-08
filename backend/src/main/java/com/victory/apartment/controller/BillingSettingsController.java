package com.victory.apartment.controller;

import com.victory.apartment.model.BillingSettings;
import com.victory.apartment.repository.BillingSettingsRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/billing-settings")
public class BillingSettingsController {
    private final BillingSettingsRepository repository;

    public BillingSettingsController(BillingSettingsRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public BillingSettings get() {
        return repository.findById("default").orElseGet(() -> {
            BillingSettings settings = new BillingSettings();
            return repository.save(settings);
        });
    }

    @PutMapping
    public BillingSettings update(@RequestBody BillingSettings request) {
        if (request.getMonthlyInterestRate() == null || request.getMonthlyInterestRate() < 0 || request.getMonthlyInterestRate() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Interest rate must be between 0 and 100 percent.");
        }
        BillingSettings settings = repository.findById("default").orElseGet(BillingSettings::new);
        settings.setMonthlyInterestRate(request.getMonthlyInterestRate());
        settings.setUpdatedAt(java.time.LocalDateTime.now());
        return repository.save(settings);
    }
}
