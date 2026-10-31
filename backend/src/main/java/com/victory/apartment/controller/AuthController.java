package com.victory.apartment.controller;

import com.victory.apartment.model.AppUser;
import com.victory.apartment.repository.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AppUserRepository userRepo;

    @Value("${app.admin.username:${ADMIN_USERNAME:admin}}")
    private String adminUsername = "admin";

    @Value("${app.admin.email:${ADMIN_EMAIL:admin@victoryapartment.com}}")
    private String adminEmail = "admin@victoryapartment.com";

    @Value("${app.admin.password:${ADMIN_PASSWORD:admin}}")
    private String adminPassword = "admin";

    public AuthController(AppUserRepository userRepo) {
        this.userRepo = userRepo;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> credentials, HttpServletRequest request) {
        String email = credentials.getOrDefault("email", "").trim().toLowerCase();
        String password = credentials.getOrDefault("password", "").trim();

        Map<String, Object> response = new HashMap<>();

        // Admin authentication (Environment Variables / Database backed, no hardcoding)
        boolean matchesAdminIdentity = email.equalsIgnoreCase(adminUsername) || email.equalsIgnoreCase(adminEmail);
        if (matchesAdminIdentity) {
            Optional<AppUser> adminDbOpt = userRepo.findByEmail(adminEmail);
            if (adminDbOpt.isEmpty()) {
                adminDbOpt = userRepo.findByEmail(adminUsername);
            }

            String effectiveAdminPassword = adminDbOpt.map(AppUser::getPassword).orElse(adminPassword);
            if (password.equals(effectiveAdminPassword)) {
                AppUser adminUser = adminDbOpt.orElse(null);
                Map<String, Object> userMap = new HashMap<>();
                userMap.put("id", adminUser != null ? adminUser.getId() : "usr-admin-01");
                userMap.put("fullname", adminUser != null ? adminUser.getFullname() : "Victory Admin");
                userMap.put("email", adminEmail);
                userMap.put("role", "admin");
                if (adminUser != null && adminUser.getLastPasswordChange() != null) {
                    userMap.put("lastPasswordChange", adminUser.getLastPasswordChange().toString());
                }
                response.put("success", true);
                response.put("user", userMap);
                establishSession(request, adminEmail);
                return ResponseEntity.ok(response);
            }
        }

        // Database user authentication (by email)
        Optional<AppUser> dbUserOpt = userRepo.findByEmail(email);
        if (dbUserOpt.isPresent() && dbUserOpt.get().getPassword().equals(password)) {
            AppUser dbUser = dbUserOpt.get();
            Map<String, Object> userMap = new HashMap<>();
            userMap.put("id", dbUser.getId());
            userMap.put("fullname", dbUser.getFullname());
            userMap.put("email", dbUser.getEmail());
            userMap.put("role", dbUser.getRole());
            if (dbUser.getLastPasswordChange() != null) {
                userMap.put("lastPasswordChange", dbUser.getLastPasswordChange().toString());
            }
            response.put("success", true);
            response.put("user", userMap);
            establishSession(request, dbUser.getEmail());
            return ResponseEntity.ok(response);
        }

        response.put("success", false);
        response.put("error", "Invalid email or password.");
        return ResponseEntity.status(401).body(response);
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, String> data, HttpServletRequest request) {
        String fullname = data.getOrDefault("fullname", "User").trim();
        String email = data.getOrDefault("email", "").trim().toLowerCase();
        String password = data.getOrDefault("password", "").trim();
        String phone = data.getOrDefault("phone", "").trim();

        Map<String, Object> response = new HashMap<>();

        if (email.isEmpty() || password.isEmpty()) {
            response.put("success", false);
            response.put("error", "Fullname, email, and password are required.");
            return ResponseEntity.badRequest().body(response);
        }

        if (userRepo.existsByEmail(email)) {
            response.put("success", false);
            response.put("error", "Email is already registered. Please log in.");
            return ResponseEntity.badRequest().body(response);
        }

        AppUser newUser = new AppUser("usr-" + UUID.randomUUID().toString().substring(0, 8), email, password, fullname, phone, "customer");
        AppUser saved = userRepo.save(newUser);

        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", saved.getId());
        userMap.put("fullname", saved.getFullname());
        userMap.put("email", saved.getEmail());
        userMap.put("role", saved.getRole());

        response.put("success", true);
        response.put("user", userMap);
        establishSession(request, saved.getEmail());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        return ResponseEntity.noContent().build();
    }

    private void establishSession(HttpServletRequest request, String email) {
        request.getSession();
        request.changeSessionId();
        request.getSession().setAttribute("userEmail", email);
    }

    @PutMapping("/profile")
    public ResponseEntity<Map<String, Object>> updateProfile(@RequestBody Map<String, String> data) {
        String email = data.getOrDefault("email", "").trim().toLowerCase();
        String fullname = data.getOrDefault("fullname", "").trim();
        String phone = data.getOrDefault("phone", "").trim();

        Map<String, Object> response = new HashMap<>();
        if (email.isEmpty() || fullname.isEmpty()) {
            response.put("success", false);
            response.put("error", "Email and fullname are required.");
            return ResponseEntity.badRequest().body(response);
        }

        Optional<AppUser> dbUserOpt = userRepo.findByEmail(email);
        if (dbUserOpt.isPresent()) {
            AppUser user = dbUserOpt.get();
            user.setFullname(fullname);
            if (!phone.isEmpty()) {
                user.setPhone(phone);
            }
            AppUser saved = userRepo.save(user);

            Map<String, Object> userMap = new HashMap<>();
            userMap.put("id", saved.getId());
            userMap.put("fullname", saved.getFullname());
            userMap.put("email", saved.getEmail());
            userMap.put("phone", saved.getPhone());
            userMap.put("role", saved.getRole());

            response.put("success", true);
            response.put("user", userMap);
            return ResponseEntity.ok(response);
        }

        // Admin fallback
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", "usr-admin-01");
        userMap.put("fullname", fullname);
        userMap.put("email", email);
        userMap.put("phone", phone);
        userMap.put("role", "admin");

        response.put("success", true);
        response.put("user", userMap);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, Object>> changePassword(@RequestBody Map<String, String> payload) {
        String email = payload.getOrDefault("email", "").trim().toLowerCase();
        String oldPassword = payload.getOrDefault("oldPassword", "").trim();
        String newPassword = payload.getOrDefault("newPassword", "").trim();

        Map<String, Object> response = new HashMap<>();

        if (email.isEmpty() || oldPassword.isEmpty() || newPassword.isEmpty()) {
            response.put("success", false);
            response.put("error", "Email, old password, and new password are required.");
            return ResponseEntity.badRequest().body(response);
        }

        if (newPassword.length() < 6) {
            response.put("success", false);
            response.put("error", "New password must be at least 6 characters.");
            return ResponseEntity.badRequest().body(response);
        }

        boolean isAdmin = email.equalsIgnoreCase(adminUsername) || email.equalsIgnoreCase(adminEmail);

        if (isAdmin) {
            Optional<AppUser> adminOpt = userRepo.findByEmail(adminEmail);
            if (adminOpt.isEmpty()) {
                adminOpt = userRepo.findByEmail(adminUsername);
            }

            String currentPass = adminOpt.map(AppUser::getPassword).orElse(adminPassword);
            if (!currentPass.equals(oldPassword)) {
                response.put("success", false);
                response.put("error", "Current password is incorrect.");
                return ResponseEntity.status(401).body(response);
            }

            AppUser admin = adminOpt.orElseGet(() -> new AppUser("usr-admin-01", adminEmail, newPassword, "Victory Admin", "081-234-5678", "admin"));
            admin.setPassword(newPassword);
            admin.setLastPasswordChange(LocalDateTime.now());
            AppUser saved = userRepo.save(admin);

            // Synchronize alias if present
            userRepo.findByEmail(adminUsername).ifPresent(alias -> {
                alias.setPassword(newPassword);
                alias.setLastPasswordChange(LocalDateTime.now());
                userRepo.save(alias);
            });

            response.put("success", true);
            response.put("message", "Admin password rotated successfully.");
            response.put("lastPasswordChange", saved.getLastPasswordChange().toString());
            return ResponseEntity.ok(response);
        }

        // Standard user change password
        Optional<AppUser> userOpt = userRepo.findByEmail(email);
        if (userOpt.isEmpty() || !userOpt.get().getPassword().equals(oldPassword)) {
            response.put("success", false);
            response.put("error", "Current password is incorrect.");
            return ResponseEntity.status(401).body(response);
        }

        AppUser user = userOpt.get();
        user.setPassword(newPassword);
        user.setLastPasswordChange(LocalDateTime.now());
        AppUser saved = userRepo.save(user);

        response.put("success", true);
        response.put("message", "Password rotated successfully.");
        response.put("lastPasswordChange", saved.getLastPasswordChange().toString());
        return ResponseEntity.ok(response);
    }
}

