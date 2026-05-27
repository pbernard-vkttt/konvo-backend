package com.vulkantechtt.konvo.ai;

import com.vulkantechtt.konvo.config.RabbitConfig;
import com.vulkantechtt.konvo.conversations.Conversation;
import com.vulkantechtt.konvo.conversations.ConversationRepository;
import com.vulkantechtt.konvo.conversations.OutboundMessageCommand;
import com.vulkantechtt.konvo.conversations.OutboundMessageDispatcher;
import com.vulkantechtt.konvo.customers.Customer;
import com.vulkantechtt.konvo.customers.CustomerRepository;
import com.vulkantechtt.konvo.knowledge.KnowledgeRetriever;
import com.vulkantechtt.konvo.tenants.Tenant;
import com.vulkantechtt.konvo.tenants.TenantRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Drains {@code konvo.ai.reply}: retrieves top-k chunks for the inbound
 * message, asks the active {@link AiCompletionProvider} to draft a reply,
 * records the run, and enqueues an outbound message through the existing M3
 * outbound pipeline.
 *
 * Re-checks {@code conversation.autoReplyEnabled} after dequeue so a user
 * toggling Vee off mid-flight stops further auto-replies (already-enqueued
 * commands still complete — acceptable race for M5).
 */
@Component
public class AiReplyListener {

    private static final Logger log = LoggerFactory.getLogger(AiReplyListener.class);
    private static final int TOP_K = 4;
    private static final int MAX_TOKENS = 300;
    private static final double TEMPERATURE = 0.4;

    private final AiCompletionProvider completion;
    private final KnowledgeRetriever retriever;
    private final ConversationRepository conversations;
    private final CustomerRepository customers;
    private final TenantRepository tenants;
    private final OutboundMessageDispatcher dispatcher;
    private final AiRunRecorder runs;
    private final com.vulkantechtt.konvo.billing.SubscriptionService subscriptions;
    private final com.vulkantechtt.konvo.billing.UsageService usage;
    private final com.vulkantechtt.konvo.notifications.NotificationService notifications;

    /**
     * Per-tenant guard so the quota-paused notification fires once per
     * listener-process lifetime per tenant, not once per dropped message.
     * Reset on bounce (acceptable — a real "Vee paused" notification daily
     * is the worst case if a tenant stays over quota across restarts).
     */
    private final java.util.Set<java.util.UUID> notifiedOverQuotaTenants =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    public AiReplyListener(
            AiCompletionProvider completion,
            KnowledgeRetriever retriever,
            ConversationRepository conversations,
            CustomerRepository customers,
            TenantRepository tenants,
            OutboundMessageDispatcher dispatcher,
            AiRunRecorder runs,
            com.vulkantechtt.konvo.billing.SubscriptionService subscriptions,
            com.vulkantechtt.konvo.billing.UsageService usage,
            com.vulkantechtt.konvo.notifications.NotificationService notifications) {
        this.completion = completion;
        this.retriever = retriever;
        this.conversations = conversations;
        this.customers = customers;
        this.tenants = tenants;
        this.dispatcher = dispatcher;
        this.runs = runs;
        this.subscriptions = subscriptions;
        this.usage = usage;
        this.notifications = notifications;
    }

    @RabbitListener(queues = RabbitConfig.AI_REPLY_QUEUE)
    public void onAiReply(AiReplyCommand cmd) {
        Conversation conv = conversations.findById(cmd.conversationId()).orElse(null);
        if (conv == null) {
            log.warn("AI reply requested for unknown conversation {}", cmd.conversationId());
            return;
        }
        if (!conv.isAutoReplyEnabled()) {
            log.debug("Auto-reply disabled mid-flight for conversation {}", conv.getId());
            return;
        }
        try {
            var sub = subscriptions.activeFor(cmd.tenantId());
            if (usage.isOverAiQuota(cmd.tenantId(), sub)) {
                log.warn("AI auto-reply skipped — tenant {} over plan {} quota",
                        cmd.tenantId(), sub.getPlan().getId());
                runs.recordFailure(cmd.tenantId(), cmd.conversationId(), "reply",
                        completion.name(), "n/a", 0, "Plan quota exceeded");
                if (notifiedOverQuotaTenants.add(cmd.tenantId())) {
                    notifications.broadcastToOwnersAndAdmins(cmd.tenantId(),
                            com.vulkantechtt.konvo.notifications.NotificationType.AI_QUOTA_PAUSED,
                            "Vee paused for this billing period",
                            "Your " + sub.getPlan().getName() + " plan quota was reached. Upgrade to resume auto-replies.",
                            "/app/settings/billing");
                }
                return;
            }
        } catch (RuntimeException e) {
            // No subscription row — should never happen post-V006 backfill, but
            // don't 500 the listener over it; treat as "no limits, proceed".
            log.warn("Could not resolve subscription for tenant {} — proceeding without quota check: {}",
                    cmd.tenantId(), e.toString());
        }
        Customer customer = customers.findById(cmd.customerId()).orElse(null);
        if (customer == null) {
            log.warn("AI reply skipped — missing customer {}", cmd.customerId());
            return;
        }
        Tenant tenant = tenants.findById(cmd.tenantId()).orElse(null);
        String workspaceName = tenant != null ? tenant.getName() : "this workspace";

        long start = System.currentTimeMillis();
        try {
            List<KnowledgeRetriever.Hit> hits = retriever.topK(cmd.tenantId(), cmd.inboundBody(), TOP_K);
            String system = PromptBuilder.systemPrompt(workspaceName, hits);
            AiCompletionProvider.CompletionRequest req = new AiCompletionProvider.CompletionRequest(
                    system, List.of(), cmd.inboundBody(), MAX_TOKENS, TEMPERATURE);
            AiCompletionProvider.Completion result = completion.complete(req);
            int latency = (int) (System.currentTimeMillis() - start);

            runs.recordOk(cmd.tenantId(), cmd.conversationId(), "reply",
                    completion.name(), result.modelId(),
                    result.promptTokens(), result.completionTokens(),
                    result.costEstimateUsd(), latency);

            String body = result.text() == null ? "" : result.text().trim();
            if (body.isEmpty()) {
                log.warn("AI returned empty reply for conversation {}", conv.getId());
                return;
            }
            dispatcher.enqueue(new OutboundMessageCommand(
                    cmd.tenantId(), cmd.conversationId(), cmd.channelId(),
                    cmd.customerId(), customer.getPhone(), body));
        } catch (Exception e) {
            int latency = (int) (System.currentTimeMillis() - start);
            log.error("AI reply failed for conversation {}", cmd.conversationId(), e);
            runs.recordFailure(cmd.tenantId(), cmd.conversationId(), "reply",
                    completion.name(), "unknown", latency, e.getMessage());
        }
    }
}
