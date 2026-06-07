package com.vulkantechtt.konvo.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** Plans are catalogue data — seeded by V006, never mutated at runtime. */
@Entity
@Table(name = "plans")
public class Plan {

    @Id
    @Column(name = "id", length = 32)
    private String id;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "monthly_price_usd", nullable = false)
    private BigDecimal monthlyPriceUsd;

    @Column(name = "monthly_price_ttd", nullable = false)
    private BigDecimal monthlyPriceTtd;

    @Column(name = "msg_monthly_limit", nullable = false)
    private int msgMonthlyLimit;

    @Column(name = "customer_monthly_limit", nullable = false)
    private int customerMonthlyLimit;

    @Column(name = "ai_runs_monthly_limit", nullable = false)
    private int aiRunsMonthlyLimit;

    @Column(name = "ai_tokens_monthly_limit", nullable = false)
    private int aiTokensMonthlyLimit;

    @Column(name = "knowledge_sources_limit", nullable = false)
    private int knowledgeSourcesLimit;

    @Column(name = "knowledge_chars_limit", nullable = false)
    private int knowledgeCharsLimit;

    @Column(name = "members_limit", nullable = false)
    private int membersLimit;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getMonthlyPriceUsd() { return monthlyPriceUsd; }
    public void setMonthlyPriceUsd(BigDecimal monthlyPriceUsd) { this.monthlyPriceUsd = monthlyPriceUsd; }

    public BigDecimal getMonthlyPriceTtd() { return monthlyPriceTtd; }
    public void setMonthlyPriceTtd(BigDecimal monthlyPriceTtd) { this.monthlyPriceTtd = monthlyPriceTtd; }

    public int getMsgMonthlyLimit() { return msgMonthlyLimit; }
    public void setMsgMonthlyLimit(int msgMonthlyLimit) { this.msgMonthlyLimit = msgMonthlyLimit; }

    public int getCustomerMonthlyLimit() { return customerMonthlyLimit; }
    public void setCustomerMonthlyLimit(int customerMonthlyLimit) { this.customerMonthlyLimit = customerMonthlyLimit; }

    public int getAiRunsMonthlyLimit() { return aiRunsMonthlyLimit; }
    public void setAiRunsMonthlyLimit(int aiRunsMonthlyLimit) { this.aiRunsMonthlyLimit = aiRunsMonthlyLimit; }

    public int getAiTokensMonthlyLimit() { return aiTokensMonthlyLimit; }
    public void setAiTokensMonthlyLimit(int aiTokensMonthlyLimit) { this.aiTokensMonthlyLimit = aiTokensMonthlyLimit; }

    public int getKnowledgeSourcesLimit() { return knowledgeSourcesLimit; }
    public void setKnowledgeSourcesLimit(int knowledgeSourcesLimit) { this.knowledgeSourcesLimit = knowledgeSourcesLimit; }

    public int getKnowledgeCharsLimit() { return knowledgeCharsLimit; }
    public void setKnowledgeCharsLimit(int knowledgeCharsLimit) { this.knowledgeCharsLimit = knowledgeCharsLimit; }

    public int getMembersLimit() { return membersLimit; }
    public void setMembersLimit(int membersLimit) { this.membersLimit = membersLimit; }

    public boolean isPublic() { return isPublic; }
    public void setPublic(boolean isPublic) { this.isPublic = isPublic; }

    public Instant getCreatedAt() { return createdAt; }
}
