package com.victory.apartment.model;

import jakarta.persistence.*;

@Entity
@Table(name = "maintenance_task_types")
public class MaintenanceTaskType {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false, unique = true)
    private String categoryKey;

    @Column(nullable = false)
    private String nameTh;

    @Column(nullable = false)
    private String nameEn;

    private Integer defaultIntervalDays;

    @Column(nullable = false)
    private Boolean isRecurring;

    public MaintenanceTaskType() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getCategoryKey() { return categoryKey; }
    public void setCategoryKey(String categoryKey) { this.categoryKey = categoryKey; }

    public String getNameTh() { return nameTh; }
    public void setNameTh(String nameTh) { this.nameTh = nameTh; }

    public String getNameEn() { return nameEn; }
    public void setNameEn(String nameEn) { this.nameEn = nameEn; }

    public Integer getDefaultIntervalDays() { return defaultIntervalDays; }
    public void setDefaultIntervalDays(Integer defaultIntervalDays) { this.defaultIntervalDays = defaultIntervalDays; }

    public Boolean getIsRecurring() { return isRecurring; }
    public void setIsRecurring(Boolean isRecurring) { this.isRecurring = isRecurring; }
}
