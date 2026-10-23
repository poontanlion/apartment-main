package com.victory.apartment.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.victory.apartment.serialization.MaskedEmergencyContactSerializer;
import com.victory.apartment.serialization.MaskedIdSerializer;
import com.victory.apartment.serialization.MaskedEmailSerializer;
import com.victory.apartment.serialization.MaskedPhoneSerializer;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "tenants")
public class Tenant {

    @Id
    private String id;

    @Column(nullable = false)
    private String fullname;

    @JsonSerialize(using = MaskedPhoneSerializer.class)
    private String phone;
    @JsonSerialize(using = MaskedEmailSerializer.class)
    private String email;
    @JsonSerialize(using = MaskedIdSerializer.class)
    private String idCardPassport;
    @JsonSerialize(using = MaskedEmergencyContactSerializer.class)
    private String emergencyContact;
    private String unitId;
    private String unitNumber;
    private LocalDateTime createdAt;

    public Tenant() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFullname() { return fullname; }
    public void setFullname(String fullname) { this.fullname = fullname; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getIdCardPassport() { return idCardPassport; }
    public void setIdCardPassport(String idCardPassport) { this.idCardPassport = idCardPassport; }

    public String getEmergencyContact() { return emergencyContact; }
    public void setEmergencyContact(String emergencyContact) { this.emergencyContact = emergencyContact; }

    public String getUnitId() { return unitId; }
    public void setUnitId(String unitId) { this.unitId = unitId; }

    public String getUnitNumber() { return unitNumber; }
    public void setUnitNumber(String unitNumber) { this.unitNumber = unitNumber; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
