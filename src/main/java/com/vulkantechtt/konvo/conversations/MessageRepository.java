package com.vulkantechtt.konvo.conversations;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    Optional<Message> findByWaMessageId(String waMessageId);

    /** Batch idempotency check for a multi-message webhook payload (M-11). */
    List<Message> findByWaMessageIdIn(Collection<String> waMessageIds);

    Page<Message> findByConversationIdOrderBySentAtAsc(UUID conversationId, Pageable pageable);

    /** Most recent customer (inbound) message — anchors the WhatsApp 24h window. */
    Optional<Message> findFirstByConversationIdAndDirectionOrderBySentAtDesc(
            UUID conversationId, MessageDirection direction);

    /** Previous messages for AI customer memory, newest first. */
    List<Message> findByConversationIdAndSentAtBeforeOrderBySentAtDesc(
            UUID conversationId, Instant sentAt, Pageable pageable);
}
