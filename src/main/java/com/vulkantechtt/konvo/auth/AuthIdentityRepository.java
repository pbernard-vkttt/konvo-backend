package com.vulkantechtt.konvo.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthIdentityRepository extends JpaRepository<AuthIdentity, UUID> {

    Optional<AuthIdentity> findByProviderAndSubject(AuthIdentityProvider provider, String subject);
}
