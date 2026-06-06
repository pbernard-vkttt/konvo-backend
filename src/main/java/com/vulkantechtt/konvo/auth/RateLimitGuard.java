package com.vulkantechtt.konvo.auth;

import com.vulkantechtt.konvo.common.KonvoException;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Token-bucket rate limiting for the public, unauthenticated auth endpoints
 * (audit H-4). BCrypt-12 already slows individual password guesses, but it does
 * not stop credential-stuffing volume, reset-token enumeration timing, or email
 * flooding — that needs per-IP and per-account throttling.
 *
 * <p>Two independent limits are enforced per action so that neither a single
 * noisy IP nor a single targeted account can be hammered:
 * <ul>
 *   <li>{@code login} — per-IP burst limit + a stricter per-email limit.</li>
 *   <li>{@code forgot-password} — per-IP limit + a strict per-email limit to
 *       cap reset-email volume to any one address.</li>
 * </ul>
 *
 * <p>Buckets are in-memory and therefore <em>per pod</em>: with N pods the
 * effective ceiling is N× a single bucket. That is a deliberate first layer;
 * the edge proxy (Traefik) or a Redis-backed bucket is the place for a global
 * limit. Stale (refilled-to-full) buckets are swept periodically so the maps
 * stay bounded.
 */
@Component
public class RateLimitGuard {

    private static final Logger log = LoggerFactory.getLogger(RateLimitGuard.class);

    /** A bucket plus the capacity it was built with, so the sweeper can tell when it's idle. */
    private record Cached(Bucket bucket, long capacity) {}

    /** capacity = max burst; one full refill of {@code capacity} tokens per {@code period}. */
    private record Rule(int capacity, Duration period) {
        Cached newBucket() {
            Bucket bucket = Bucket.builder()
                    .addLimit(limit -> limit.capacity(capacity).refillGreedy(capacity, period))
                    .build();
            return new Cached(bucket, capacity);
        }
    }

    // Tuned conservative defaults. Burst is the capacity; sustained rate is
    // capacity/period once the burst is spent.
    private static final Rule LOGIN_PER_IP = new Rule(15, Duration.ofMinutes(5));
    private static final Rule LOGIN_PER_EMAIL = new Rule(8, Duration.ofMinutes(15));
    private static final Rule FORGOT_PER_IP = new Rule(5, Duration.ofMinutes(15));
    private static final Rule FORGOT_PER_EMAIL = new Rule(4, Duration.ofHours(1));
    private static final Rule VERIFY_RESEND_PER_IP = new Rule(5, Duration.ofMinutes(15));
    private static final Rule VERIFY_RESEND_PER_EMAIL = new Rule(3, Duration.ofHours(1));

    private final ConcurrentHashMap<String, Cached> buckets = new ConcurrentHashMap<>();
    private final boolean enabled;

    public RateLimitGuard(@Value("${konvo.ratelimit.enabled:true}") boolean enabled) {
        this.enabled = enabled;
    }

    /** Throttle a login attempt by both source IP and target email. */
    public void checkLogin(String ip, String email) {
        if (!enabled) return;
        consume("login:ip:" + ip, LOGIN_PER_IP, "Too many login attempts from this network");
        consume("login:email:" + normalize(email), LOGIN_PER_EMAIL, "Too many login attempts for this account");
    }

    /** Throttle a forgot-password request by both source IP and target email. */
    public void checkForgotPassword(String ip, String email) {
        if (!enabled) return;
        consume("forgot:ip:" + ip, FORGOT_PER_IP, "Too many password-reset requests from this network");
        consume("forgot:email:" + normalize(email), FORGOT_PER_EMAIL, "Too many password-reset requests for this account");
    }

    /** Throttle verification resend requests by both source IP and signed-in email. */
    public void checkEmailVerificationResend(String ip, String email) {
        if (!enabled) return;
        consume("verify-email:ip:" + ip, VERIFY_RESEND_PER_IP, "Too many verification emails from this network");
        consume("verify-email:email:" + normalize(email), VERIFY_RESEND_PER_EMAIL, "Too many verification emails for this account");
    }

    private void consume(String key, Rule rule, String message) {
        Cached cached = buckets.computeIfAbsent(key, k -> rule.newBucket());
        ConsumptionProbe probe = cached.bucket().tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            int retryAfter = (int) Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()));
            log.warn("Rate limit hit for {} — retry after {}s", key, retryAfter);
            throw KonvoException.tooManyRequests(message, retryAfter);
        }
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Drop buckets that have refilled to capacity (i.e. no active limiting) so
     * the maps don't grow unbounded under churn. Removing a full bucket is
     * equivalent to never having created it.
     */
    @Scheduled(fixedDelayString = "${konvo.ratelimit.sweep-ms:600000}")
    void sweepIdleBuckets() {
        buckets.entrySet().removeIf(e -> e.getValue().bucket().getAvailableTokens() >= e.getValue().capacity());
    }
}
