package com.vulkantechtt.konvo.tenants;

import com.vulkantechtt.konvo.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "tenants")
public class Tenant extends BaseEntity {

    public static final int DEFAULT_CUSTOMER_MEMORY_MESSAGE_LIMIT = 12;
    public static final int MAX_CUSTOMER_MEMORY_MESSAGE_LIMIT = 50;
    public static final int MAX_WORKING_HOURS_LENGTH = 10_000;
    public static final int MAX_BUSINESS_OFFERINGS_LENGTH = 50_000;
    public static final int MAX_CUSTOM_SYSTEM_PROMPT_LENGTH = 300;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "slug", nullable = false, length = 80, unique = true)
    private String slug;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode = "TT";

    @Column(name = "plan", nullable = false, length = 32)
    private String plan = "free";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TenantStatus status = TenantStatus.active;

    @Column(name = "customer_memory_message_limit", nullable = false)
    private int customerMemoryMessageLimit = DEFAULT_CUSTOMER_MEMORY_MESSAGE_LIMIT;

    @Column(name = "working_hours", nullable = false, columnDefinition = "text")
    private String workingHours = "";

    @Column(name = "business_offerings", nullable = false, columnDefinition = "text")
    private String businessOfferings = "";

    @Column(name = "custom_system_prompt", nullable = false, length = MAX_CUSTOM_SYSTEM_PROMPT_LENGTH)
    private String customSystemPrompt = "";

    @Column(name = "industry", nullable = false, length = 80)
    private String industry = "";

    @Column(name = "onboarding_completed", nullable = false)
    private boolean onboardingCompleted = false;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }

    public String getPlan() { return plan; }
    public void setPlan(String plan) { this.plan = plan; }

    public TenantStatus getStatus() { return status; }
    public void setStatus(TenantStatus status) { this.status = status; }

    public int getCustomerMemoryMessageLimit() { return customerMemoryMessageLimit; }
    public void setCustomerMemoryMessageLimit(int customerMemoryMessageLimit) {
        this.customerMemoryMessageLimit = customerMemoryMessageLimit;
    }

    public String getWorkingHours() { return workingHours; }
    public void setWorkingHours(String workingHours) { this.workingHours = workingHours; }

    public String getBusinessOfferings() { return businessOfferings; }
    public void setBusinessOfferings(String businessOfferings) { this.businessOfferings = businessOfferings; }

    public String getCustomSystemPrompt() { return customSystemPrompt; }
    public void setCustomSystemPrompt(String customSystemPrompt) { this.customSystemPrompt = customSystemPrompt; }

    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }

    public boolean isOnboardingCompleted() { return onboardingCompleted; }
    public void setOnboardingCompleted(boolean onboardingCompleted) { this.onboardingCompleted = onboardingCompleted; }
}
