package com.vulkantechtt.konvo.knowledge;

import com.vulkantechtt.konvo.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "knowledge_sources")
public class KnowledgeSource extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "source_key", length = 64)
    private String sourceKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private KnowledgeSourceType type = KnowledgeSourceType.text;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private KnowledgeSourceStatus status = KnowledgeSourceStatus.indexing;

    @Column(name = "content", columnDefinition = "text")
    private String content;

    @Column(name = "char_count", nullable = false)
    private int charCount;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSourceKey() { return sourceKey; }
    public void setSourceKey(String sourceKey) { this.sourceKey = sourceKey; }

    public KnowledgeSourceType getType() { return type; }
    public void setType(KnowledgeSourceType type) { this.type = type; }

    public KnowledgeSourceStatus getStatus() { return status; }
    public void setStatus(KnowledgeSourceStatus status) { this.status = status; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public int getCharCount() { return charCount; }
    public void setCharCount(int charCount) { this.charCount = charCount; }

    public int getChunkCount() { return chunkCount; }
    public void setChunkCount(int chunkCount) { this.chunkCount = chunkCount; }

    public UUID getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(UUID createdByUserId) { this.createdByUserId = createdByUserId; }
}
