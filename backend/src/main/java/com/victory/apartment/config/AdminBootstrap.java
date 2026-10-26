
package com.victory.apartment.config;

import com.victory.apartment.model.AppUser;
import com.victory.apartment.repository.AppUserRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import java.util.Arrays;

@Component
public class AdminBootstrap {

    private final AppUserRepository userRepo;
    private final Environment environment;

    @Value("${app.admin.email:${ADMIN_EMAIL:admin@victoryapartment.com}}")
    private String adminEmail;

    @Value("${app.admin.password:${ADMIN_PASSWORD:admin}}")
    private String adminPassword;

    @Value("${app.admin.username:${ADMIN_USERNAME:admin}}")
    private String adminUsername;

    public AdminBootstrap(
            AppUserRepository userRepo,
            Environment environment) {
        this.userRepo = userRepo;
        this.environment = environment;
    }

    @PostConstruct
    public void initializeAdmin() {

        boolean production = Arrays.asList(
                environment.getActiveProfiles()
        ).contains("prod");

        // Production must not use the default admin password.
        if (production &&
                (adminPassword == null
                        || adminPassword.isBlank()
                        || "admin".equals(adminPassword))) {
            throw new IllegalStateException(
                    "Production requires a secure ADMIN_PASSWORD"
            );
        }

        if (!userRepo.existsByEmail(adminEmail)) {
            AppUser admin = new AppUser(
                    "usr-admin-01",
                    adminEmail,
                    adminPassword,
                    "Victory Admin",
                    "099-999-9999",
                    "admin"
            );
            userRepo.save(admin);
        }

        // Preserve the original admin alias behavior.
        if (!adminEmail.equalsIgnoreCase(adminUsername)
                && !userRepo.existsByEmail(adminUsername)) {

            AppUser adminAlias = new AppUser(
                    "usr-admin-02",
                    adminUsername,
                    adminPassword,
                    "Victory Admin",
                    "099-999-9999",
                    "admin"
            );

            userRepo.save(adminAlias);
        }
    }
}
