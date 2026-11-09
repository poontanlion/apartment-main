package com.victory.apartment.controller;

import com.victory.apartment.model.BillingSettings;
import com.victory.apartment.repository.BillingSettingsRepository;
import org.springframework.http.HttpStatus;
import java.util.List;
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

    /** Bank details and QR image for invoices. Blank values clear a field. */
    public record BankInfoRequest(String bankName, String accountName, String accountNumber, String qrCodeImage) {}

    private static final int MAX_TEXT_LENGTH = 100;
    private static final int MAX_ACCOUNT_NUMBER_LENGTH = 30;
    private static final int MAX_QR_LENGTH = 1_500_000; // ~1.1 MB of image once decoded
    private static final List<String> QR_PREFIXES = List.of(
            "data:image/png;base64,", "data:image/jpeg;base64,", "data:image/webp;base64,");

    @PutMapping("/bank-info")
    public BillingSettings updateBankInfo(@RequestBody BankInfoRequest request) {
        String bankName = cleanText(request.bankName(), "Bank name");
        String accountName = cleanText(request.accountName(), "Account name");
        String accountNumber = cleanAccountNumber(request.accountNumber());
        String qrCodeImage = cleanQrImage(request.qrCodeImage());

        BillingSettings settings = repository.findById("default").orElseGet(BillingSettings::new);
        settings.setBankName(bankName);
        settings.setAccountName(accountName);
        settings.setAccountNumber(accountNumber);
        settings.setQrCodeImage(qrCodeImage);
        settings.setUpdatedAt(java.time.LocalDateTime.now());
        return repository.save(settings);
    }

    private static String cleanText(String value, String field) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        if (trimmed.length() > MAX_TEXT_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be at most " + MAX_TEXT_LENGTH + " characters.");
        }
        return trimmed;
    }

    private static String cleanAccountNumber(String value) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        if (trimmed.length() > MAX_ACCOUNT_NUMBER_LENGTH || !trimmed.matches("[0-9 -]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Account number may only contain digits, spaces and hyphens (max " + MAX_ACCOUNT_NUMBER_LENGTH + ").");
        }
        return trimmed;
    }

    private static String cleanQrImage(String value) {
        if (value == null || value.isBlank()) return null;
        if (value.length() > MAX_QR_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "QR code image is too large. Use an image under 1 MB.");
        }
        if (QR_PREFIXES.stream().noneMatch(value::startsWith)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "QR code must be a PNG, JPEG or WebP image.");
        }
        return value;
    }
}
