package com.vulkantechtt.konvo.tenants.dto;

import com.vulkantechtt.konvo.tenants.Tenant;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateTenantSettingsRequest(
        @NotNull
        @Min(0)
        @Max(Tenant.MAX_CUSTOMER_MEMORY_MESSAGE_LIMIT)
        Integer customerMemoryMessageLimit,

        @Size(max = Tenant.MAX_WORKING_HOURS_LENGTH)
        String workingHours,

        @Size(max = Tenant.MAX_BUSINESS_OFFERINGS_LENGTH)
        String businessOfferings) {
}
