package com.victory.apartment.service.maintenancepredictor;

import com.victory.apartment.model.*;
import com.victory.apartment.repository.*;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MaintenancePredictionDigestService {
    private static final Logger logger = LoggerFactory.getLogger(MaintenancePredictionDigestService.class);
    private final MaintenancePredictionRepository predictions;
    private final MaintenanceTaskTypeRepository types;
    private final AppUserRepository users;
    private final AppNotificationRepository notifications;
    private final Clock clock;
    private final String adminEmail;
    private final TransactionTemplate transaction;

    public MaintenancePredictionDigestService(MaintenancePredictionRepository predictions,
            MaintenanceTaskTypeRepository types, AppUserRepository users, AppNotificationRepository notifications,
            Clock clock, PlatformTransactionManager manager,
            @Value("${app.admin.email:${ADMIN_EMAIL:admin@victoryapartment.com}}") String adminEmail) {
        this.predictions = predictions;
        this.types = types;
        this.users = users;
        this.notifications = notifications;
        this.clock = clock.withZone(PredictionConfig.ZONE);
        this.adminEmail = adminEmail;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public record DigestResult(int created, int duplicate, int failure) {}

    public DigestResult sendDailyDigest() {
        var today = LocalDate.now(clock);
        var recurring = types.findAll().stream().filter(t -> Boolean.TRUE.equals(t.getIsRecurring()))
                .map(MaintenanceTaskType::getId).collect(Collectors.toSet());
        long overdue = 0;
        long dueSoon = 0;
        for (var prediction : predictions.findAll()) {
            if (!recurring.contains(prediction.getTaskTypeId()) || prediction.getPredictedDate() == null) continue;
            var status = PredictionStatus.from(prediction.getPredictedDate(), today);
            if (status == PredictionStatus.OVERDUE) overdue++;
            else if (status == PredictionStatus.DUE_SOON) dueSoon++;
        }
        if (overdue + dueSoon == 0) return new DigestResult(0, 0, 0);

        var allUsers = users.findAll();
        Set<String> recipients = new TreeSet<>();
        allUsers.stream().filter(user -> "admin".equalsIgnoreCase(user.getRole()))
                .map(AppUser::getEmail).filter(Objects::nonNull).filter(email -> !email.isBlank())
                .map(email -> email.toLowerCase(Locale.ROOT)).forEach(recipients::add);
        // Match prediction endpoint semantics: a customer row blocks the fallback.
        if (allUsers.stream().noneMatch(user -> adminEmail.equalsIgnoreCase(user.getEmail())))
            recipients.add(adminEmail.toLowerCase(Locale.ROOT));

        int created = 0;
        int duplicate = 0;
        int failure = 0;
        String message = "Overdue: " + overdue + " items; due soon (within 7 days): " + dueSoon
                + " items. View maintenance predictions: /admin/maintenance?tab=predictions";
        for (String recipient : recipients) {
            try {
                transaction.executeWithoutResult(ignored -> {
                    var notification = new AppNotification();
                    notification.setId("notif-" + UUID.randomUUID().toString().substring(0, 8));
                    notification.setUserId(recipient);
                    notification.setTitle("Maintenance Predictions Daily Digest");
                    notification.setMessage(message);
                    notification.setType("warning");
                    notification.setIsRead(false);
                    notification.setCreatedAt(LocalDateTime.now(clock));
                    notification.setDedupeKey("maint-pred-digest:" + today);
                    notifications.saveAndFlush(notification);
                });
                created++;
            } catch (DataIntegrityViolationException exception) {
                if (isDigestDuplicate(exception)) duplicate++;
                else {
                    failure++;
                    logger.error("Maintenance digest persistence failed for admin {}", recipient, exception);
                }
            } catch (RuntimeException exception) {
                failure++;
                logger.error("Maintenance digest failed for admin {}", recipient, exception);
            }
        }
        return new DigestResult(created, duplicate, failure);
    }

    private boolean isDigestDuplicate(Throwable exception) {
        for (var cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException constraint && "23505".equals(constraint.getSQLState())
                    && "uk_app_notifications_recipient_dedupe".equals(constraint.getConstraintName())) return true;
        }
        return false;
    }
}
