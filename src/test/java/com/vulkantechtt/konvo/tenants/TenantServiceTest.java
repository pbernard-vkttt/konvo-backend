package com.vulkantechtt.konvo.tenants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vulkantechtt.konvo.audit.AuditAction;
import com.vulkantechtt.konvo.audit.AuditService;
import com.vulkantechtt.konvo.security.KonvoPrincipal;
import com.vulkantechtt.konvo.tenants.dto.UpdateTenantSettingsRequest;
import com.vulkantechtt.konvo.users.Role;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock TenantRepository tenants;
    @Mock AuditService audit;

    private TenantService service;

    @BeforeEach
    void setUp() {
        service = new TenantService(tenants, audit);
    }

    @Test
    void meReturnsCustomerMemorySetting() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = tenant(tenantId, 18);
        when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));

        var response = service.me(tenantId);

        assertThat(response.customerMemoryMessageLimit()).isEqualTo(18);
    }

    @Test
    void updateSettingsPersistsAndAuditsChangedMemoryLimit() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = tenant(tenantId, 12);
        when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(tenants.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.updateSettings(
                principal(tenantId),
                new UpdateTenantSettingsRequest(24));

        assertThat(response.customerMemoryMessageLimit()).isEqualTo(24);
        verify(audit).record(
                any(KonvoPrincipal.class),
                org.mockito.ArgumentMatchers.eq(AuditAction.WORKSPACE_SETTINGS_UPDATED),
                org.mockito.ArgumentMatchers.eq(tenantId),
                org.mockito.ArgumentMatchers.eq("Updated workspace settings"),
                org.mockito.ArgumentMatchers.<Map<String, ?>>any());
    }

    @Test
    void updateSettingsSkipsAuditWhenLimitIsUnchanged() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = tenant(tenantId, 12);
        when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(tenants.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateSettings(principal(tenantId), new UpdateTenantSettingsRequest(12));

        verify(audit, never()).record(any(), any(), any(), any(), any());
    }

    private static Tenant tenant(UUID id, int memoryLimit) {
        Tenant t = new Tenant();
        t.setId(id);
        t.setName("Acme Co");
        t.setSlug("acme");
        t.setCustomerMemoryMessageLimit(memoryLimit);
        return t;
    }

    private static KonvoPrincipal principal(UUID tenantId) {
        return new KonvoPrincipal(UUID.randomUUID(), "owner@example.com", "Owner", tenantId, Role.OWNER);
    }
}
