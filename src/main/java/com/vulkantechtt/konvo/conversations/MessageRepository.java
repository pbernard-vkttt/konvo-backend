package com.vulkantechtt.konvo.conversations;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    Optional<Message> findByWaMessageId(String waMessageId);
}
