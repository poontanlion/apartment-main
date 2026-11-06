package com.victory.apartment.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** A room's price from {@code effectiveMonth} (always the 1st) until the next row for that room. */
@Entity
@Table(name = "room_prices")
public class RoomPrice {

    @Id
    private String id;

    @Column(nullable = false)
    private String roomId;

    @Column(nullable = false)
    private Double price;

    @Column(nullable = false)
    private LocalDate effectiveMonth;

    private LocalDateTime createdAt;

    public RoomPrice() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }

    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }

    public LocalDate getEffectiveMonth() { return effectiveMonth; }
    public void setEffectiveMonth(LocalDate effectiveMonth) { this.effectiveMonth = effectiveMonth; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
