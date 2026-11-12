package com.victory.apartment.service;

import com.victory.apartment.config.BusinessMetricsCollector;
import com.victory.apartment.model.Booking;
import com.victory.apartment.model.MaintenanceTask;
import com.victory.apartment.model.Room;
import com.victory.apartment.model.UtilityBill;
import com.victory.apartment.repository.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class BusinessMetricsCollectorTest {

    private MeterRegistry meterRegistry;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private UtilityBillRepository utilityBillRepository;

    @Mock
    private MaintenanceTaskRepository maintenanceTaskRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private MaintenancePredictionRepository maintenancePredictionRepository;

    private BusinessMetricsCollector collector;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        collector = new BusinessMetricsCollector(
                meterRegistry,
                roomRepository,
                utilityBillRepository,
                maintenanceTaskRepository,
                bookingRepository,
                maintenancePredictionRepository
        );
        collector.registerGauges();
    }

    @Test
    @DisplayName("Gauges calculate occupancy rate and inventory accurately")
    void testRoomMetricsCalculation() {
        Room r1 = new Room();
        r1.setStatus("Occupied");
        Room r2 = new Room();
        r2.setStatus("Occupied");
        Room r3 = new Room();
        r3.setStatus("Available");
        Room r4 = new Room();
        r4.setStatus("Maintenance");

        when(roomRepository.findAll()).thenReturn(List.of(r1, r2, r3, r4));
        when(utilityBillRepository.findAll()).thenReturn(List.of());
        when(maintenanceTaskRepository.findAll()).thenReturn(List.of());
        when(bookingRepository.findAll()).thenReturn(List.of());
        when(maintenancePredictionRepository.count()).thenReturn(5L);

        collector.refreshMetrics();

        assertEquals(4.0, meterRegistry.get("apartment.rooms.total").gauge().value());
        assertEquals(2.0, meterRegistry.get("apartment.rooms.occupied").gauge().value());
        assertEquals(1.0, meterRegistry.get("apartment.rooms.available").gauge().value());
        assertEquals(1.0, meterRegistry.get("apartment.rooms.maintenance").gauge().value());
        assertEquals(0.5, meterRegistry.get("apartment.rooms.occupancy.rate").gauge().value(), 0.001);
        assertEquals(5.0, meterRegistry.get("apartment.maintenance.predictions.total").gauge().value());
    }

    @Test
    @DisplayName("Gauges calculate financial and overdue metrics accurately")
    void testFinancialMetricsCalculation() {
        UtilityBill b1 = new UtilityBill();
        b1.setStatus("Unpaid");
        b1.setTotalAmount(5000.0);
        b1.setBalanceDue(5000.0);
        b1.setPaidAmount(0.0);

        UtilityBill b2 = new UtilityBill();
        b2.setStatus("Paid");
        b2.setTotalAmount(4500.0);
        b2.setBalanceDue(0.0);
        b2.setPaidAmount(4500.0);

        when(roomRepository.findAll()).thenReturn(List.of());
        when(utilityBillRepository.findAll()).thenReturn(List.of(b1, b2));
        when(maintenanceTaskRepository.findAll()).thenReturn(List.of());
        when(bookingRepository.findAll()).thenReturn(List.of());

        collector.refreshMetrics();

        assertEquals(1.0, meterRegistry.get("apartment.bills.unpaid.count").gauge().value());
        assertEquals(5000.0, meterRegistry.get("apartment.bills.outstanding.amount").gauge().value());
        assertEquals(4500.0, meterRegistry.get("apartment.bills.paid.amount").gauge().value());
        assertEquals(9500.0, meterRegistry.get("apartment.bills.total.invoiced").gauge().value());
    }

    @Test
    @DisplayName("Gauges track maintenance tasks and booking demand")
    void testOperationsAndDemandMetrics() {
        MaintenanceTask t1 = new MaintenanceTask();
        t1.setStatus("Pending");
        t1.setPriority("High");

        MaintenanceTask t2 = new MaintenanceTask();
        t2.setStatus("In Progress");
        t2.setPriority("Low");

        MaintenanceTask t3 = new MaintenanceTask();
        t3.setStatus("Completed");
        t3.setPriority("High");

        Booking bk1 = new Booking();
        bk1.setStatus("Pending");

        Booking bk2 = new Booking();
        bk2.setStatus("Approved");

        when(roomRepository.findAll()).thenReturn(List.of());
        when(utilityBillRepository.findAll()).thenReturn(List.of());
        when(maintenanceTaskRepository.findAll()).thenReturn(List.of(t1, t2, t3));
        when(bookingRepository.findAll()).thenReturn(List.of(bk1, bk2));

        collector.refreshMetrics();

        assertEquals(2.0, meterRegistry.get("apartment.maintenance.tasks.active").gauge().value());
        assertEquals(1.0, meterRegistry.get("apartment.maintenance.tasks.high_priority").gauge().value());
        assertEquals(1.0, meterRegistry.get("apartment.bookings.pending").gauge().value());
    }
}
