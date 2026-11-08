package com.victory.apartment.repository;

import com.victory.apartment.model.BillingSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingSettingsRepository extends JpaRepository<BillingSettings, String> {}
