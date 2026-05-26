package com.vulkantechtt.konvo.conversations;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    Optional<Conversation> findByChannelIdAndCustomerId(UUID channelId, UUID customerId);
}
