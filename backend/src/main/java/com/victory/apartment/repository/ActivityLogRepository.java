package com.victory.apartment.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.victory.apartment.model.ActivityLog;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, String> {
    List<ActivityLog> findTop50ByOrderByCreatedAtDesc();
}

