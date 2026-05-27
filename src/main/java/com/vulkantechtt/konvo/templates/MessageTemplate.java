package com.vulkantechtt.konvo.templates;

import com.vulkantechtt.konvo.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "message_templates")
public class MessageTemplate extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "language", nullable = false, length = 16)
    private String language;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 24)
    private TemplateCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TemplateStatus status;

    /** Meta's structured component tree, kept as raw JSON for forward-compat. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "components", columnDefinition = "jsonb")
    private String components;

    @Column(name = "meta_template_id", length = 64)
    private String metaTemplateId;

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public TemplateCategory getCategory() { return category; }
    public void setCategory(TemplateCategory category) { this.category = category; }

    public TemplateStatus getStatus() { return status; }
    public void setStatus(TemplateStatus status) { this.status = status; }

    public String getComponents() { return components; }
    public void setComponents(String components) { this.components = components; }

    public String getMetaTemplateId() { return metaTemplateId; }
    public void setMetaTemplateId(String metaTemplateId) { this.metaTemplateId = metaTemplateId; }
}
