package com.vulkantechtt.konvo.auth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserInvitationRepository extends JpaRepository<UserInvitation, UUID> {

    Optional<UserInvitation> findByTokenHash(String tokenHash);

    List<UserInvitation> findByTenantIdAndAcceptedAtIsNullAndRevokedAtIsNull(UUID tenantId);

    List<UserInvitation> findByEmailIgnoreCaseAndAcceptedAtIsNullAndRevokedAtIsNull(String email);

    Optional<UserInvitation> findByTenantIdAndEmailIgnoreCaseAndAcceptedAtIsNullAndRevokedAtIsNull(
            UUID tenantId, String email);
}
