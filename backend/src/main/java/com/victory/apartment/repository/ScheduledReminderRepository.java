package com.victory.apartment.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.victory.apartment.model.ScheduledReminder;

public interface ScheduledReminderRepository extends JpaRepository<ScheduledReminder, String> {
}

