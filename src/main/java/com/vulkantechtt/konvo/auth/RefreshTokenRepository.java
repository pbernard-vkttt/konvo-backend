package com.vulkantechtt.konvo.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("""
            update RefreshToken t
               set t.revokedAt = :revokedAt
             where t.userId = :userId
               and t.revokedAt is null
            """)
    int revokeActiveByUserId(@Param("userId") UUID userId, @Param("revokedAt") Instant revokedAt);
}
