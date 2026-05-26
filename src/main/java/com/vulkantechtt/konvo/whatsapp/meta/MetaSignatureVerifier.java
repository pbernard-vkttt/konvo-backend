package com.vulkantechtt.konvo.whatsapp.meta;

import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Verifies Meta's {@code X-Hub-Signature-256} header. Meta computes
 * {@code "sha256=" + HMAC_SHA256(app_secret, raw_request_body)} and ships it
 * in this header. We recompute on our side and constant-time compare.
 *
 * The verifier intentionally takes raw bytes — a re-serialised JSON string
 * (with whitespace or key-ordering changes) would produce a different MAC.
 */
public final class MetaSignatureVerifier {

    private static final String HEADER_PREFIX = "sha256=";

    private MetaSignatureVerifier() {}

    public static boolean verify(String appSecret, byte[] rawBody, String headerValue) {
        if (appSecret == null || appSecret.isEmpty() || rawBody == null || headerValue == null) {
            return false;
        }
        if (!headerValue.startsWith(HEADER_PREFIX)) {
            return false;
        }
        String presented = headerValue.substring(HEADER_PREFIX.length());
        String expected = computeHex(appSecret, rawBody);
        return constantTimeEquals(expected, presented);
    }

    static String computeHex(String secret, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] out = mac.doFinal(body);
            StringBuilder hex = new StringBuilder(out.length * 2);
            for (byte b : out) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
