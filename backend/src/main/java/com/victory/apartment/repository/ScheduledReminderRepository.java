package com.victory.apartment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.victory.apartment.model.ScheduledReminder;

import java.util.Collection;

public interface ScheduledReminderRepository extends JpaRepository<ScheduledReminder, String> {
    java.util.List<ScheduledReminder> findByRoomIdAndCategory(String roomId, String category);

    @Modifying
    @Transactional
    @Query("delete from ScheduledReminder r where r.roomId = :roomId")
    void deleteByRoomId(@Param("roomId") String roomId);

    @Modifying
    @Transactional
    @Query("delete from ScheduledReminder r where r.roomId in :roomIds")
    void deleteByRoomIdIn(@Param("roomIds") Collection<String> roomIds);
}
