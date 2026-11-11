package com.victory.apartment.config;

import com.victory.apartment.model.Booking;
import com.victory.apartment.model.MaintenanceTask;
import com.victory.apartment.model.Room;
import com.victory.apartment.model.UtilityBill;
import com.victory.apartment.repository.*;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Collects domain-specific Business Value Metrics for Victory Apartment.
 * Exposed through Spring Boot Actuator at /actuator/prometheus and visualized in Grafana.
 */
@Component
public class BusinessMetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(BusinessMetricsCollector.class);

    private final MeterRegistry meterRegistry;
    private final RoomRepository roomRepository;
    private final UtilityBillRepository utilityBillRepository;
    private final MaintenanceTaskRepository maintenanceTaskRepository;
    private final BookingRepository bookingRepository;
    private final MaintenancePredictionRepository maintenancePredictionRepository;

    // Room Metrics
    private final AtomicLong totalRooms = new AtomicLong(0);
    private final AtomicLong occupiedRooms = new AtomicLong(0);
    private final AtomicLong availableRooms = new AtomicLong(0);
    private final AtomicLong maintenanceRooms = new AtomicLong(0);
    private final AtomicReference<Double> occupancyRate = new AtomicReference<>(0.0);

    // Financial / Utility Bill Metrics
    private final AtomicLong unpaidBillsCount = new AtomicLong(0);
    private final AtomicLong outstandingAmount = new AtomicLong(0);
    private final AtomicLong paidAmount = new AtomicLong(0);
    private final AtomicLong totalInvoicedAmount = new AtomicLong(0);

    // Operations & Maintenance Metrics
    private final AtomicLong activeMaintenanceTasks = new AtomicLong(0);
    private final AtomicLong highPriorityMaintenanceTasks = new AtomicLong(0);
    private final AtomicLong totalPredictions = new AtomicLong(0);

    // Booking Demand Metrics
    private final AtomicLong pendingBookings = new AtomicLong(0);

    public BusinessMetricsCollector(
            MeterRegistry meterRegistry,
            RoomRepository roomRepository,
            UtilityBillRepository utilityBillRepository,
            MaintenanceTaskRepository maintenanceTaskRepository,
            BookingRepository bookingRepository,
            MaintenancePredictionRepository maintenancePredictionRepository) {
        this.meterRegistry = meterRegistry;
        this.roomRepository = roomRepository;
        this.utilityBillRepository = utilityBillRepository;
        this.maintenanceTaskRepository = maintenanceTaskRepository;
        this.bookingRepository = bookingRepository;
        this.maintenancePredictionRepository = maintenancePredictionRepository;
    }

    @PostConstruct
    public void registerGauges() {
        meterRegistry.gauge("apartment.rooms.total", totalRooms);
        meterRegistry.gauge("apartment.rooms.occupied", occupiedRooms);
        meterRegistry.gauge("apartment.rooms.available", availableRooms);
        meterRegistry.gauge("apartment.rooms.maintenance", maintenanceRooms);
        meterRegistry.gauge("apartment.rooms.occupancy.rate", occupancyRate, AtomicReference::get);

        meterRegistry.gauge("apartment.bills.unpaid.count", unpaidBillsCount);
        meterRegistry.gauge("apartment.bills.outstanding.amount", outstandingAmount);
        meterRegistry.gauge("apartment.bills.paid.amount", paidAmount);
        meterRegistry.gauge("apartment.bills.total.invoiced", totalInvoicedAmount);

        meterRegistry.gauge("apartment.maintenance.tasks.active", activeMaintenanceTasks);
        meterRegistry.gauge("apartment.maintenance.tasks.high_priority", highPriorityMaintenanceTasks);
        meterRegistry.gauge("apartment.maintenance.predictions.total", totalPredictions);

        meterRegistry.gauge("apartment.bookings.pending", pendingBookings);

        log.info("Registered apartment business value metrics with Micrometer.");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        refreshMetrics();
    }

    @Scheduled(fixedRate = 15000) // Collect every 15 seconds
    public void refreshMetrics() {
        try {
            collectRoomMetrics();
            collectFinancialMetrics();
            collectMaintenanceMetrics();
            collectBookingMetrics();
        } catch (Exception e) {
            log.warn("Failed to collect business metrics: {}", e.getMessage());
        }
    }

    private void collectRoomMetrics() {
        List<Room> rooms = roomRepository.findAll();
        long total = rooms.size();
        long occupied = 0;
        long available = 0;
        long maintenance = 0;

        for (Room room : rooms) {
            String status = room.getStatus();
            if (status != null) {
                if (status.equalsIgnoreCase("Occupied")) {
                    occupied++;
                } else if (status.equalsIgnoreCase("Available")) {
                    available++;
                } else if (status.equalsIgnoreCase("Maintenance")) {
                    maintenance++;
                }
            }
        }

        totalRooms.set(total);
        occupiedRooms.set(occupied);
        availableRooms.set(available);
        maintenanceRooms.set(maintenance);
        occupancyRate.set(total > 0 ? (double) occupied / total : 0.0);
    }

    private void collectFinancialMetrics() {
        List<UtilityBill> bills = utilityBillRepository.findAll();
        long unpaidCount = 0;
        double sumOutstanding = 0.0;
        double sumPaid = 0.0;
        double sumInvoiced = 0.0;

        for (UtilityBill bill : bills) {
            String status = bill.getStatus();
            Double balanceDue = bill.getBalanceDue();
            Double paid = bill.getPaidAmount();
            Double total = bill.getTotalAmount();

            if (balanceDue != null && balanceDue > 0.01) {
                sumOutstanding += balanceDue;
            }
            if (paid != null) {
                sumPaid += paid;
            }
            if (total != null) {
                sumInvoiced += total;
            }

            if (status != null && !status.equalsIgnoreCase("Paid")) {
                unpaidCount++;
            }
        }

        unpaidBillsCount.set(unpaidCount);
        outstandingAmount.set(Math.round(sumOutstanding));
        paidAmount.set(Math.round(sumPaid));
        totalInvoicedAmount.set(Math.round(sumInvoiced));
    }

    private void collectMaintenanceMetrics() {
        List<MaintenanceTask> tasks = maintenanceTaskRepository.findAll();
        long active = 0;
        long highPriority = 0;

        for (MaintenanceTask task : tasks) {
            String status = task.getStatus();
            if (status != null && !status.equalsIgnoreCase("Completed")) {
                active++;
                if ("High".equalsIgnoreCase(task.getPriority())) {
                    highPriority++;
                }
            }
        }

        activeMaintenanceTasks.set(active);
        highPriorityMaintenanceTasks.set(highPriority);
        totalPredictions.set(maintenancePredictionRepository.count());
    }

    private void collectBookingMetrics() {
        List<Booking> bookings = bookingRepository.findAll();
        long pending = 0;
        for (Booking booking : bookings) {
            if ("Pending".equalsIgnoreCase(booking.getStatus())) {
                pending++;
            }
        }
        pendingBookings.set(pending);
    }
}
