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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Short-lived single-use ticket exchanged for an SSE stream. The browser's
 * {@code EventSource} can't set the {@code Authorization} header — passing
 * the long-lived access token as a query string would leak it into nginx /
 * proxy logs, so we issue a 60-second one-shot token instead.
 *
 * Storage swaps in based on Redis availability: when a {@link StringRedisTemplate}
 * is wired, tickets live in Redis with a 60-second TTL so any pod can redeem
 * (multi-pod, M8). Otherwise tickets live in an in-process map (single-pod
 * dev). Both paths consume the ticket on first redemption.
 */
@Service
public class SseTicketService {

    private static final Duration TTL = Duration.ofSeconds(60);
    private static final String REDIS_PREFIX = "sse-ticket:";

    private final Map<String, Ticket> localTickets = new ConcurrentHashMap<>();
    private final StringRedisTemplate redis;

    public SseTicketService(ObjectProvider<StringRedisTemplate> redisProvider) {
        // Wired only when KONVO_REALTIME_BUS=redis (and spring.data.redis.host
        // is set so the auto-config attaches). Otherwise tickets live in
        // localTickets — fine for the dev native run.
        this.redis = redisProvider.getIfAvailable();
    }

    public String issue(KonvoPrincipal principal) {
        String raw = TokenHasher.randomToken();
        Instant expiresAt = Instant.now().plus(TTL);
        Ticket t = new Ticket(principal.userId(), principal.tenantId(), principal.role(), expiresAt);
        if (redis != null) {
            redis.opsForValue().set(REDIS_PREFIX + raw, serialise(t), TTL);
        } else {
            purgeExpired();
            localTickets.put(raw, t);
        }
        return raw;
    }

    /** Consumes the ticket on the first successful redemption. */
    public Optional<Ticket> redeem(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        if (redis != null) {
            String key = REDIS_PREFIX + raw;
            String stored = redis.opsForValue().getAndDelete(key);
            if (stored == null) return Optional.empty();
            Ticket t = deserialise(stored);
            if (t == null || Instant.now().isAfter(t.expiresAt())) return Optional.empty();
            return Optional.of(t);
        }
        Ticket t = localTickets.remove(raw);
        if (t == null) return Optional.empty();
        if (Instant.now().isAfter(t.expiresAt())) return Optional.empty();
        return Optional.of(t);
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        localTickets.entrySet().removeIf(e -> now.isAfter(e.getValue().expiresAt()));
    }

    private static String serialise(Ticket t) {
        return t.userId() + "|" + t.tenantId() + "|" + t.role().name() + "|" + t.expiresAt().toEpochMilli();
    }

    private static Ticket deserialise(String stored) {
        try {
            String[] parts = stored.split("\\|");
            return new Ticket(
                    UUID.fromString(parts[0]),
                    UUID.fromString(parts[1]),
                    Role.valueOf(parts[2]),
                    Instant.ofEpochMilli(Long.parseLong(parts[3])));
        } catch (Exception e) {
            return null;
        }
    }

    public record Ticket(UUID userId, UUID tenantId, Role role, Instant expiresAt) {}
}
