package com.vulkantechtt.konvo.conversations;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    Optional<Message> findByWaMessageId(String waMessageId);

    /** Batch idempotency check for a multi-message webhook payload (M-11). */
    List<Message> findByWaMessageIdIn(Collection<String> waMessageIds);

    @Query(
            value = """
                    select m from Message m
                    where m.conversationId = :conversationId
                    order by m.sentAt desc, m.id desc
                    """,
            countQuery = """
                    select count(m) from Message m
                    where m.conversationId = :conversationId
                    """)
    Page<Message> findLatestByConversationId(
            @Param("conversationId") UUID conversationId,
            Pageable pageable);

    /** Most recent customer (inbound) message — anchors the WhatsApp 24h window. */
    Optional<Message> findFirstByConversationIdAndDirectionOrderBySentAtDesc(
            UUID conversationId, MessageDirection direction);

    /** Previous messages for AI customer memory, newest first. */
    List<Message> findByConversationIdAndSentAtBeforeOrderBySentAtDesc(
            UUID conversationId, Instant sentAt, Pageable pageable);
}
