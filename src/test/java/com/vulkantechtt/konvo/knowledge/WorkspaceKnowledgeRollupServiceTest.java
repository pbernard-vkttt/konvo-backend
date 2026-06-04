package com.vulkantechtt.konvo.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vulkantechtt.konvo.security.KonvoPrincipal;
import com.vulkantechtt.konvo.tenants.Tenant;
import com.vulkantechtt.konvo.users.Role;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class WorkspaceKnowledgeRollupServiceTest {

    @Mock KnowledgeSourceRepository sources;
    @Mock KnowledgeIndexer indexer;

    @InjectMocks WorkspaceKnowledgeRollupService service;

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void syncCreatesManagedSourceAndIndexesAfterCommit() {
        UUID sourceId = UUID.randomUUID();
        Tenant tenant = tenant();
        tenant.setWorkingHours("Mon-Fri, 9 am to 5 pm");
        tenant.setBusinessOfferings("Breakfast menu\nLunch specials");
        when(sources.findByTenantIdAndSourceKey(
                tenant.getId(), WorkspaceKnowledgeRollupService.SOURCE_KEY))
                .thenReturn(Optional.empty());
        when(sources.save(any(KnowledgeSource.class))).thenAnswer(inv -> {
            KnowledgeSource source = inv.getArgument(0);
            source.setId(sourceId);
            return source;
        });

        TransactionSynchronizationManager.initSynchronization();

        service.sync(principal(tenant.getId()), tenant);

        ArgumentCaptor<KnowledgeSource> sourceCaptor = ArgumentCaptor.forClass(KnowledgeSource.class);
        verify(sources).save(sourceCaptor.capture());
        KnowledgeSource saved = sourceCaptor.getValue();
        assertThat(saved.getSourceKey()).isEqualTo(WorkspaceKnowledgeRollupService.SOURCE_KEY);
        assertThat(saved.getTitle()).isEqualTo("Workspace profile");
        assertThat(saved.getStatus()).isEqualTo(KnowledgeSourceStatus.indexing);
        assertThat(saved.getContent()).contains("Working hours", "Services, products, and menus");
        verify(indexer, never()).indexAsync(any());

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        verify(indexer).indexAsync(sourceId);
    }

    @Test
    void syncDeletesManagedSourceWhenWorkspaceProfileIsBlank() {
        Tenant tenant = tenant();
        KnowledgeSource existing = new KnowledgeSource();
        existing.setId(UUID.randomUUID());
        existing.setTenantId(tenant.getId());
        existing.setSourceKey(WorkspaceKnowledgeRollupService.SOURCE_KEY);
        when(sources.findByTenantIdAndSourceKey(
                tenant.getId(), WorkspaceKnowledgeRollupService.SOURCE_KEY))
                .thenReturn(Optional.of(existing));

        service.sync(principal(tenant.getId()), tenant);

        verify(sources).delete(existing);
        verify(sources, never()).save(any());
        verify(indexer, never()).indexAsync(any());
    }

    @Test
    void syncSkipsReindexWhenGeneratedContentIsUnchangedAndReady() {
        Tenant tenant = tenant();
        tenant.setWorkingHours("Mon-Fri, 9 am to 5 pm");
        KnowledgeSource existing = new KnowledgeSource();
        existing.setId(UUID.randomUUID());
        existing.setTenantId(tenant.getId());
        existing.setSourceKey(WorkspaceKnowledgeRollupService.SOURCE_KEY);
        existing.setStatus(KnowledgeSourceStatus.ready);
        existing.setContent(WorkspaceKnowledgeRollupService.buildContent(tenant));
        when(sources.findByTenantIdAndSourceKey(
                tenant.getId(), WorkspaceKnowledgeRollupService.SOURCE_KEY))
                .thenReturn(Optional.of(existing));
        when(sources.save(any(KnowledgeSource.class))).thenAnswer(inv -> inv.getArgument(0));

        service.sync(principal(tenant.getId()), tenant);

        verify(sources).save(existing);
        verify(indexer, never()).indexAsync(any());
    }

    private static Tenant tenant() {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName("Acme Co");
        tenant.setSlug("acme");
        return tenant;
    }

    private static KonvoPrincipal principal(UUID tenantId) {
        return new KonvoPrincipal(UUID.randomUUID(), "owner@example.com", "Owner", tenantId, Role.OWNER);
    }
}
