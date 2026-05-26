package com.vulkantechtt.konvo.customers;

import com.vulkantechtt.konvo.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Minimal M3 shape. M4 grows tags, lifecycle stage, custom fields, profile
 * picture URL, etc. The webhook ingest path is the only writer today, so
 * there's no controller/service for customers in this milestone.
 */
@Entity
@Table(name = "customers")
public class Customer extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "phone", nullable = false, length = 32)
    private String phone;

    @Column(name = "display_name", length = 160)
    private String displayName;

    @Column(name = "profile_name", length = 160)
    private String profileName;

    @Column(name = "locale", length = 16)
    private String locale;

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getProfileName() { return profileName; }
    public void setProfileName(String profileName) { this.profileName = profileName; }

    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }
}
