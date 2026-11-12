package com.victory.apartment.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.victory.apartment.model.AppNotification;

public interface AppNotificationRepository extends JpaRepository<AppNotification, String> {
    boolean existsByDedupeKey(String dedupeKey);
    List<AppNotification> findAllByOrderByCreatedAtDesc();
}

