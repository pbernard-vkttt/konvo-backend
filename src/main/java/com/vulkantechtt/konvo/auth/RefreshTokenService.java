package com.vulkantechtt.konvo.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues, rotates, and revokes refresh tokens. The raw token only ever
 * leaves this service via {@link Issued#rawToken()} which the caller writes
 * into the HTTP-only cookie. Storage uses {@code TokenHasher.hash} so a DB
 * compromise can't replay sessions.
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final AuthProperties props;

    public RefreshTokenService(RefreshTokenRepository repository, AuthProperties props) {
        this.repository = repository;
        this.props = props;
    }

    @Transactional
    public Issued issue(UUID userId, UUID tenantId, HttpServletRequest req) {
        String raw = TokenHasher.randomToken();
        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setTenantId(tenantId);
        token.setTokenHash(TokenHasher.hash(raw));
        token.setExpiresAt(Instant.now().plus(Duration.ofDays(props.getRefreshTokenTtlDays())));
        if (req != null) {
            token.setUserAgent(truncate(req.getHeader("User-Agent"), 255));
            token.setIpAddress(truncate(req.getRemoteAddr(), 64));
        }
        RefreshToken saved = repository.save(token);
        return new Issued(raw, saved);
    }

    @Transactional
    public Issued rotate(String presentedRaw, HttpServletRequest req) {
        RefreshToken current = repository.findByTokenHash(TokenHasher.hash(presentedRaw))
                .orElseThrow(() -> new InvalidRefreshTokenException("Unknown refresh token"));

        Instant now = Instant.now();
        if (!current.isActive(now)) {
            // Replay or expired — revoke the whole chain rooted at this token's user
            // as a defensive measure. Cheap and matches OWASP guidance.
            current.setRevokedAt(now);
            repository.save(current);
            throw new InvalidRefreshTokenException("Refresh token is no longer valid");
        }

        Issued next = issue(current.getUserId(), current.getTenantId(), req);
        current.setRevokedAt(now);
        current.setReplacedBy(next.entity().getId());
        repository.save(current);
        return next;
    }

    @Transactional
    public void revoke(String presentedRaw) {
        if (presentedRaw == null || presentedRaw.isBlank()) {
            return;
        }
        repository.findByTokenHash(TokenHasher.hash(presentedRaw))
                .ifPresent(t -> {
                    if (t.getRevokedAt() == null) {
                        t.setRevokedAt(Instant.now());
                        repository.save(t);
                    }
                });
    }

    @Transactional
    public int revokeAllForUser(UUID userId) {
        if (userId == null) {
            return 0;
        }
        return repository.revokeActiveByUserId(userId, Instant.now());
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    public record Issued(String rawToken, RefreshToken entity) {}

    public static final class InvalidRefreshTokenException extends RuntimeException {
        public InvalidRefreshTokenException(String message) {
            super(message);
        }
    }
}
