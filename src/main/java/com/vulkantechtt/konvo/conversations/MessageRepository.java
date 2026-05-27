package com.vulkantechtt.konvo.conversations;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    Optional<Message> findByWaMessageId(String waMessageId);

    Page<Message> findByConversationIdOrderBySentAtAsc(UUID conversationId, Pageable pageable);
}
