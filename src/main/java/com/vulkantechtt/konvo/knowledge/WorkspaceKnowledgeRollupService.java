package com.vulkantechtt.konvo.knowledge;

import com.vulkantechtt.konvo.security.KonvoPrincipal;
import com.vulkantechtt.konvo.tenants.Tenant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class WorkspaceKnowledgeRollupService {

    public static final String SOURCE_KEY = "workspace-profile";
    private static final String SOURCE_TITLE = "Workspace profile";

    private final KnowledgeSourceRepository sources;
    private final KnowledgeIndexer indexer;

    public WorkspaceKnowledgeRollupService(KnowledgeSourceRepository sources, KnowledgeIndexer indexer) {
        this.sources = sources;
        this.indexer = indexer;
    }

    @Transactional
    public void sync(KonvoPrincipal actor, Tenant tenant) {
        String content = buildContent(tenant);
        var existing = sources.findByTenantIdAndSourceKey(tenant.getId(), SOURCE_KEY);
        if (content.isBlank()) {
            existing.ifPresent(sources::delete);
            return;
        }

        KnowledgeSource source = existing.orElseGet(() -> newSource(actor, tenant.getId()));
        boolean shouldIndex = !Objects.equals(source.getContent(), content)
                || source.getStatus() == KnowledgeSourceStatus.failed;

        source.setTitle(SOURCE_TITLE);
        source.setContent(content);
        source.setCharCount(content.length());
        if (shouldIndex) {
            source.setStatus(KnowledgeSourceStatus.indexing);
            source.setChunkCount(0);
        }

        KnowledgeSource saved = sources.save(source);
        if (shouldIndex) {
            dispatchIndexAfterCommit(saved.getId());
        }
    }

    static String buildContent(Tenant tenant) {
        String workingHours = normalize(tenant.getWorkingHours());
        String businessOfferings = normalize(tenant.getBusinessOfferings());
        if (workingHours.isBlank() && businessOfferings.isBlank()) {
            return "";
        }

        StringBuilder content = new StringBuilder();
        content.append("Workspace profile for ").append(tenant.getName()).append("\n\n");
        if (!workingHours.isBlank()) {
            content.append("Working hours\n");
            content.append(workingHours).append("\n\n");
        }
        if (!businessOfferings.isBlank()) {
            content.append("Services, products, and menus\n");
            content.append(businessOfferings).append("\n");
        }
        return content.toString().strip();
    }

    private static KnowledgeSource newSource(KonvoPrincipal actor, UUID tenantId) {
        KnowledgeSource source = new KnowledgeSource();
        source.setTenantId(tenantId);
        source.setSourceKey(SOURCE_KEY);
        source.setType(KnowledgeSourceType.text);
        source.setCreatedByUserId(actor.userId());
        return source;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .strip();
    }

    private void dispatchIndexAfterCommit(UUID sourceId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            indexer.indexAsync(sourceId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                indexer.indexAsync(sourceId);
            }
        });
    }
}
