package com.vulkantechtt.konvo.realtime;

import com.vulkantechtt.konvo.auth.TokenHasher;
import com.vulkantechtt.konvo.security.KonvoPrincipal;
import com.vulkantechtt.konvo.users.Role;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Short-lived single-use ticket exchanged for an SSE stream. The browser's
 * {@code EventSource} can't set the {@code Authorization} header — passing
 * the long-lived access token as a query string would leak it into nginx /
 * proxy logs, so we issue a 60-second one-shot token instead.
 *
 * In-memory only; a multi-instance deployment moves this into Redis.
 */
@Service
public class SseTicketService {

    private static final Duration TTL = Duration.ofSeconds(60);

    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();

    public String issue(KonvoPrincipal principal) {
        purgeExpired();
        String raw = TokenHasher.randomToken();
        tickets.put(raw, new Ticket(
                principal.userId(),
                principal.tenantId(),
                principal.role(),
                Instant.now().plus(TTL)));
        return raw;
    }

    /** Consumes the ticket on the first successful redemption. */
    public Optional<Ticket> redeem(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        Ticket t = tickets.remove(raw);
        if (t == null) return Optional.empty();
        if (Instant.now().isAfter(t.expiresAt())) return Optional.empty();
        return Optional.of(t);
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        tickets.entrySet().removeIf(e -> now.isAfter(e.getValue().expiresAt()));
    }

    public record Ticket(UUID userId, UUID tenantId, Role role, Instant expiresAt) {}
}
