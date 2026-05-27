package com.vulkantechtt.konvo.users;

import com.vulkantechtt.konvo.tenants.Tenant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantMembershipRepository extends JpaRepository<TenantMembership, UUID> {

    List<TenantMembership> findByUserIdAndStatus(UUID userId, MembershipStatus status);

    List<TenantMembership> findByTenantIdAndStatus(UUID tenantId, MembershipStatus status);

    Optional<TenantMembership> findByTenantIdAndUserId(UUID tenantId, UUID userId);

    Optional<TenantMembership> findByTenantAndUser(Tenant tenant, User user);

    boolean existsByTenantIdAndUserIdAndStatus(UUID tenantId, UUID userId, MembershipStatus status);

    long countByTenantIdAndRoleAndStatus(UUID tenantId, Role role, MembershipStatus status);

    List<TenantMembership> findByTenantIdAndRoleInAndStatus(UUID tenantId, List<Role> roles, MembershipStatus status);
}
