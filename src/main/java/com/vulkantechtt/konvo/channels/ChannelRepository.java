package com.vulkantechtt.konvo.channels;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelRepository extends JpaRepository<Channel, UUID> {

    List<Channel> findByTenantId(UUID tenantId);

    Optional<Channel> findByPhoneNumberId(String phoneNumberId);

    boolean existsByTenantIdAndProvider(UUID tenantId, ChannelProvider provider);
}
