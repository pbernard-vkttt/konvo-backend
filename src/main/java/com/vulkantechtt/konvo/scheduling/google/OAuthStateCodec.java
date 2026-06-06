package com.vulkantechtt.konvo.scheduling.google;

import com.vulkantechtt.konvo.common.KonvoException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Signs/verifies the OAuth {@code state} parameter so the stateless Google
 * callback (which arrives without a JWT) can trust which tenant/user initiated
 * the connect. Payload {@code tenantId:userId:expiryEpoch} is HMAC-SHA256'd with
 * the JWT signing secret. Short-lived (minutes) to limit replay.
 */
@Component
public class OAuthStateCodec {

    private static final long TTL_SECONDS = 600;
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private final byte[] key;

    public OAuthStateCodec(@Value("${konvo.security.jwt.signing-secret}") String secret) {
        this.key = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String issue(UUID tenantId, UUID userId) {
        String payload = tenantId + ":" + userId + ":" + (System.currentTimeMillis() / 1000 + TTL_SECONDS);
        String encoded = B64.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encoded + "." + sign(encoded);
    }

    /** Verifies signature + expiry and returns the bound state. */
    public State verify(String state) {
        if (state == null || !state.contains(".")) {
            throw badState();
        }
        int dot = state.lastIndexOf('.');
        String encoded = state.substring(0, dot);
        String sig = state.substring(dot + 1);
        if (!constantTimeEquals(sig, sign(encoded))) {
            throw badState();
        }
        String payload = new String(B64D.decode(encoded), StandardCharsets.UTF_8);
        String[] parts = payload.split(":");
        if (parts.length != 3) {
            throw badState();
        }
        long expiry = parseLong(parts[2]);
        if (System.currentTimeMillis() / 1000 > expiry) {
            throw new KonvoException(HttpStatus.BAD_REQUEST, "oauth_state_expired",
                    "The Google connection link expired. Please try again.");
        }
        try {
            return new State(UUID.fromString(parts[0]), UUID.fromString(parts[1]));
        } catch (IllegalArgumentException e) {
            throw badState();
        }
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return B64.encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Could not sign OAuth state", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static KonvoException badState() {
        return new KonvoException(HttpStatus.BAD_REQUEST, "oauth_state_invalid",
                "Invalid Google connection request");
    }

    public record State(UUID tenantId, UUID userId) {}
}
