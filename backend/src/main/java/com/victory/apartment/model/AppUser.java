package com.victory.apartment.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.victory.apartment.serialization.MaskedEmailSerializer;
import com.victory.apartment.serialization.MaskedPhoneSerializer;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class AppUser {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    @JsonSerialize(using = MaskedEmailSerializer.class)
    private String email;

    @JsonIgnore
    @Column(nullable = false)
    private String password;

    private String fullname;
    @JsonSerialize(using = MaskedPhoneSerializer.class)
    private String phone;
    private String role; // "admin" or "customer"
    private LocalDateTime createdAt;
    private LocalDateTime lastPasswordChange;

    public AppUser() {}

    public AppUser(String id, String email, String password, String fullname, String phone, String role) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.fullname = fullname;
        this.phone = phone;
        this.role = role;
        this.createdAt = LocalDateTime.now();
        this.lastPasswordChange = LocalDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getFullname() { return fullname; }
    public void setFullname(String fullname) { this.fullname = fullname; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getLastPasswordChange() { return lastPasswordChange; }
    public void setLastPasswordChange(LocalDateTime lastPasswordChange) { this.lastPasswordChange = lastPasswordChange; }
}

