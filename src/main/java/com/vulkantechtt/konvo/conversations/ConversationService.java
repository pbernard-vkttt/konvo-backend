package com.vulkantechtt.konvo.conversations;

import com.vulkantechtt.konvo.channels.Channel;
import com.vulkantechtt.konvo.channels.ChannelProvider;
import com.vulkantechtt.konvo.channels.ChannelRepository;
import com.vulkantechtt.konvo.common.KonvoException;
import com.vulkantechtt.konvo.common.PageResponse;
import com.vulkantechtt.konvo.conversations.dto.ConversationDetail;
import com.vulkantechtt.konvo.conversations.dto.ConversationSummary;
import com.vulkantechtt.konvo.customers.Customer;
import com.vulkantechtt.konvo.customers.CustomerRepository;
import com.vulkantechtt.konvo.notifications.NotificationService;
import com.vulkantechtt.konvo.notifications.NotificationType;
import com.vulkantechtt.konvo.security.KonvoPrincipal;
import com.vulkantechtt.konvo.users.Role;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Conversation read/write for the inbox. Visibility: owners/admins/managers see
 * every thread in their tenant; agents/viewers see threads either assigned to
 * them or unassigned (mirrors the conventional CRM rule "an agent picks up
 * unassigned chats; once they reply they own it"). Managers match admins for
 * everything inside the inbox (full visibility + assigning agents) but hold no
 * admin powers elsewhere.
 */
@Service
public class ConversationService {

    /**
     * WhatsApp's customer service window: free-form replies are only allowed
     * within 24h of the customer's last inbound message. After that, only
     * approved templates can be sent. See {@link #assertWhatsAppWindowOpen}.
     */
    public static final Duration WHATSAPP_SESSION_WINDOW = Duration.ofHours(24);

    private final ConversationRepository conversations;
    private final CustomerRepository customers;
    private final ChannelRepository channels;
    private final MessageRepository messages;
    private final NotificationService notifications;

    public ConversationService(ConversationRepository conversations,
                               CustomerRepository customers,
                               ChannelRepository channels,
                               MessageRepository messages,
                               NotificationService notifications) {
        this.conversations = conversations;
        this.customers = customers;
        this.channels = channels;
        this.messages = messages;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public PageResponse<ConversationSummary> list(
            KonvoPrincipal principal,
            ConversationStatus status,
            String search,
            Pageable pageable) {
        UUID restrict = seesAllChats(principal) ? null : principal.userId();
        Page<Conversation> rows = conversations.search(
                principal.tenantId(), status, search, restrict, pageable);
        return PageResponse.from(rows.map(c -> toSummary(c, customers.findById(c.getCustomerId()).orElse(null))));
    }

    @Transactional(readOnly = true)
    public ConversationDetail get(KonvoPrincipal principal, UUID id) {
        Conversation c = requireVisible(principal, id);
        Customer cu = customers.findById(c.getCustomerId()).orElse(null);
        Channel ch = channels.findById(c.getChannelId()).orElse(null);
        return new ConversationDetail(
                c.getId(),
                c.getChannelId(),
                ch != null ? ch.getDisplayName() : null,
                ch != null ? ch.getProvider().name() : null,
                c.getCustomerId(),
                cu != null ? displayNameOf(cu) : null,
                cu != null ? cu.getPhone() : null,
                c.getStatus(),
                c.getAssignedUserId(),
                c.isAutoReplyEnabled(),
                c.getLastMessageAt(),
                lastInboundAt(c.getId()),
                c.getCreatedAt());
    }

    @Transactional
    public ConversationDetail setAutoReply(KonvoPrincipal principal, UUID id, boolean enabled) {
        Conversation c = requireVisible(principal, id);
        c.setAutoReplyEnabled(enabled);
        conversations.save(c);
        return get(principal, id);
    }

    @Transactional
    public ConversationDetail updateStatus(KonvoPrincipal principal, UUID id, ConversationStatus newStatus) {
        Conversation c = requireVisible(principal, id);
        c.setStatus(newStatus);
        conversations.save(c);
        return get(principal, id);
    }

    @Transactional
    public ConversationDetail assign(KonvoPrincipal principal, UUID id, UUID assigneeUserId) {
        Conversation c = requireVisible(principal, id);
        // Self-assignment is allowed for any role; cross-assignment requires
        // owner/admin/manager (managers are the inbox supervisors).
        if (assigneeUserId != null && !assigneeUserId.equals(principal.userId())
                && !seesAllChats(principal)) {
            throw KonvoException.forbidden("Only owners, admins and managers can assign other agents");
        }
        UUID previous = c.getAssignedUserId();
        c.setAssignedUserId(assigneeUserId);
        conversations.save(c);
        if (assigneeUserId != null
                && !assigneeUserId.equals(previous)
                && !assigneeUserId.equals(principal.userId())) {
            Customer cu = customers.findById(c.getCustomerId()).orElse(null);
            String who = cu != null ? displayNameOf(cu) : "a customer";
            notifications.notifyUser(principal.tenantId(), assigneeUserId,
                    NotificationType.CONVERSATION_ASSIGNED,
                    "Conversation assigned to you",
                    "From " + who + " — assigned by " + principal.fullName(),
                    "/app/inbox?conversation=" + c.getId());
        }
        return get(principal, id);
    }

    Conversation requireVisible(KonvoPrincipal principal, UUID id) {
        Conversation c = conversations.findById(id)
                .orElseThrow(() -> KonvoException.notFound("Conversation", id));
        if (!c.getTenantId().equals(principal.tenantId())) {
            throw KonvoException.notFound("Conversation", id);
        }
        if (!seesAllChats(principal)) {
            UUID assigned = c.getAssignedUserId();
            if (assigned != null && !assigned.equals(principal.userId())) {
                throw KonvoException.forbidden("That conversation is assigned to another agent");
            }
        }
        return c;
    }

    /** Roles that see and manage every conversation in the tenant. */
    private static boolean seesAllChats(KonvoPrincipal principal) {
        return sees(principal, Role.OWNER, Role.ADMIN, Role.MANAGER);
    }

    private static boolean sees(KonvoPrincipal principal, Role... roles) {
        for (Role r : roles) if (principal.role() == r) return true;
        return false;
    }

    private static ConversationSummary toSummary(Conversation c, Customer cu) {
        return new ConversationSummary(
                c.getId(),
                c.getChannelId(),
                c.getCustomerId(),
                cu != null ? displayNameOf(cu) : null,
                cu != null ? cu.getPhone() : null,
                c.getStatus(),
                c.getAssignedUserId(),
                c.getLastMessageAt(),
                c.getLastMessagePreview());
    }

    static String displayNameOf(Customer cu) {
        if (cu.getDisplayName() != null && !cu.getDisplayName().isBlank()) return cu.getDisplayName();
        if (cu.getProfileName() != null && !cu.getProfileName().isBlank()) return cu.getProfileName();
        return cu.getPhone();
    }

    // Exposed for cross-package use (e.g. MessageService) so the visibility
    // rules don't drift.
    public Conversation requireVisibleConversation(KonvoPrincipal principal, UUID id) {
        return requireVisible(principal, id);
    }

    /** Timestamp of the customer's most recent inbound message, or null if none. */
    Instant lastInboundAt(UUID conversationId) {
        return messages
                .findFirstByConversationIdAndDirectionOrderBySentAtDesc(conversationId, MessageDirection.inbound)
                .map(Message::getSentAt)
                .orElse(null);
    }

    /**
     * Guards the WhatsApp 24h customer service window. No-op for non-WhatsApp
     * channels. Throws if the channel is WhatsApp and either the customer has
     * never messaged or their last inbound is older than the window — in which
     * case only an approved template (not a free-form reply) may be sent.
     */
    public void assertWhatsAppWindowOpen(Conversation conv) {
        Channel ch = channels.findById(conv.getChannelId()).orElse(null);
        if (ch == null || !isWhatsApp(ch.getProvider())) {
            return;
        }
        Instant lastInbound = lastInboundAt(conv.getId());
        if (lastInbound == null || Instant.now().isAfter(lastInbound.plus(WHATSAPP_SESSION_WINDOW))) {
            throw KonvoException.badRequest(
                    "The 24-hour WhatsApp reply window has closed. Send an approved template to reopen the chat.");
        }
    }

    private static boolean isWhatsApp(ChannelProvider provider) {
        return provider != null && provider.name().startsWith("whatsapp");
    }

}
