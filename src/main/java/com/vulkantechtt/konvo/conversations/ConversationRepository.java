package com.vulkantechtt.konvo.conversations;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    Optional<Conversation> findByChannelIdAndCustomerId(UUID channelId, UUID customerId);

    /**
     * Inbox list. Filters by tenant + optional status + free-text search across
     * the linked customer's display_name, profile_name, and phone. Ordered by
     * last_message_at desc so the freshest thread bubbles up.
     *
     * Visibility filter: when restrictToUserId is non-null, only return
     * conversations either assigned to that user or unassigned. Owners/admins
     * pass null to see everything in the tenant.
     */
    @Query("""
            select c from Conversation c
            where c.tenantId = :tenantId
              and (:status is null or c.status = :status)
              and (
                :search is null
                or :search = ''
                or exists (
                  select 1 from Customer cu
                  where cu.id = c.customerId
                    and (
                      lower(cu.displayName) like concat('%', lower(cast(:search as string)), '%')
                      or lower(cu.profileName) like concat('%', lower(cast(:search as string)), '%')
                      or cu.phone like concat('%', cast(:search as string), '%')
                    )
                )
              )
              and (
                :restrictToUserId is null
                or c.assignedUserId = :restrictToUserId
                or c.assignedUserId is null
              )
            order by c.lastMessageAt desc nulls last, c.createdAt desc
            """)
    Page<Conversation> search(
            @Param("tenantId") UUID tenantId,
            @Param("status") ConversationStatus status,
            @Param("search") String search,
            @Param("restrictToUserId") UUID restrictToUserId,
            Pageable pageable);
}
