package com.vulkantechtt.konvo.knowledge;

import com.vulkantechtt.konvo.common.KonvoException;
import com.vulkantechtt.konvo.common.PageResponse;
import com.vulkantechtt.konvo.knowledge.dto.CreateTextSourceRequest;
import com.vulkantechtt.konvo.knowledge.dto.KnowledgeSourceResponse;
import com.vulkantechtt.konvo.security.KonvoPrincipal;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD for knowledge sources. Creates persist the source as {@code indexing}
 * then hand off to {@link KnowledgeIndexer} which chunks + embeds + flips to
 * {@code ready}. Reads are paginated; deletes cascade chunks via FK.
 */
@Service
public class KnowledgeService {

    private final KnowledgeSourceRepository sources;
    private final KnowledgeIndexer indexer;

    public KnowledgeService(KnowledgeSourceRepository sources, KnowledgeIndexer indexer) {
        this.sources = sources;
        this.indexer = indexer;
    }

    @Transactional(readOnly = true)
    public PageResponse<KnowledgeSourceResponse> list(KonvoPrincipal principal, Pageable pageable) {
        return PageResponse.from(sources
                .findByTenantIdOrderByCreatedAtDesc(principal.tenantId(), pageable)
                .map(KnowledgeService::toResponse));
    }

    @Transactional(readOnly = true)
    public KnowledgeSourceResponse get(KonvoPrincipal principal, UUID id) {
        return toResponse(requireOwned(principal, id));
    }

    @Transactional
    public KnowledgeSourceResponse createText(KonvoPrincipal principal, CreateTextSourceRequest req) {
        KnowledgeSource source = new KnowledgeSource();
        source.setTenantId(principal.tenantId());
        source.setTitle(req.title());
        source.setType(KnowledgeSourceType.text);
        source.setStatus(KnowledgeSourceStatus.indexing);
        source.setContent(req.content());
        source.setCharCount(req.content().length());
        source.setCreatedByUserId(principal.userId());
        KnowledgeSource saved = sources.save(source);
        indexer.indexAsync(saved.getId());
        return toResponse(saved);
    }

    @Transactional
    public void delete(KonvoPrincipal principal, UUID id) {
        KnowledgeSource source = requireOwned(principal, id);
        sources.delete(source); // chunks cascade
    }

    private KnowledgeSource requireOwned(KonvoPrincipal principal, UUID id) {
        KnowledgeSource source = sources.findById(id)
                .orElseThrow(() -> KonvoException.notFound("Source", id));
        if (!source.getTenantId().equals(principal.tenantId())) {
            throw KonvoException.notFound("Source", id);
        }
        return source;
    }

    static KnowledgeSourceResponse toResponse(KnowledgeSource s) {
        return new KnowledgeSourceResponse(
                s.getId(),
                s.getTitle(),
                s.getType(),
                s.getStatus(),
                s.getCharCount(),
                s.getChunkCount(),
                s.getCreatedAt(),
                s.getUpdatedAt());
    }
}
