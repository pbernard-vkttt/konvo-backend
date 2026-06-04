package com.vulkantechtt.konvo.auth;

import com.vulkantechtt.konvo.common.BaseEntity;
import com.vulkantechtt.konvo.users.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "auth_identities")
public class AuthIdentity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 32)
    private AuthIdentityProvider provider;

    @Column(name = "subject", nullable = false, length = 255)
    private String subject;

    @Column(name = "email", length = 254)
    private String email;

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public AuthIdentityProvider getProvider() { return provider; }
    public void setProvider(AuthIdentityProvider provider) { this.provider = provider; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
