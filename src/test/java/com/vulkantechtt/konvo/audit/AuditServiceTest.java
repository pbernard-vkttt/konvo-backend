package com.vulkantechtt.konvo.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vulkantechtt.konvo.security.KonvoPrincipal;
import com.vulkantechtt.konvo.users.Role;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock AuditLogRepository repo;

    @Test
    void recordsActionWithSerialisedDiff() {
        AuditService service = new AuditService(repo, new ObjectMapper());
        UUID tenant = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        KonvoPrincipal principal = new KonvoPrincipal(actorId, "boss@x.tt", "Boss", tenant, Role.OWNER);
        when(repo.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        service.record(principal, AuditAction.MEMBER_INVITED, entityId,
                "Invited a@x.tt as agent",
                Map.of("email", "a@x.tt", "role", "AGENT"));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repo).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getTenantId()).isEqualTo(tenant);
        assertThat(saved.getActorUserId()).isEqualTo(actorId);
        assertThat(saved.getActorEmail()).isEqualTo("boss@x.tt");
        assertThat(saved.getAction()).isEqualTo("member.invited");
        assertThat(saved.getEntityType()).isEqualTo("membership");
        assertThat(saved.getEntityId()).isEqualTo(entityId);
        assertThat(saved.getDiff()).contains("\"email\":\"a@x.tt\"");
    }

    @Test
    void systemRecordHasNoActorIdButLogsSystemString() {
        AuditService service = new AuditService(repo, new ObjectMapper());
        UUID tenant = UUID.randomUUID();
        when(repo.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        service.recordSystem(tenant, AuditAction.SUBSCRIPTION_PROVISIONED, UUID.randomUUID(),
                "Workspace created on Free", Map.of("plan", "free"));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repo).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getActorUserId()).isNull();
        assertThat(saved.getActorEmail()).isEqualTo("system");
        assertThat(saved.getEntityType()).isEqualTo("subscription");
    }

    @Test
    void blankDiffIsSkipped() {
        AuditService service = new AuditService(repo, new ObjectMapper());
        when(repo.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));
        KonvoPrincipal principal = new KonvoPrincipal(UUID.randomUUID(), "x@x.tt", "X",
                UUID.randomUUID(), Role.OWNER);

        service.record(principal, AuditAction.TEMPLATE_SYNCED, null, "Synced 0", Map.of());

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getDiff()).isNull();
    }
}
