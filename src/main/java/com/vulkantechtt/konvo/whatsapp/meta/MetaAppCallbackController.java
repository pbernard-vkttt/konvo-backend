package com.vulkantechtt.konvo.whatsapp.meta;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * App-level Meta platform callbacks — distinct from the per-channel webhook at
 * {@code /api/webhooks/meta/{channelId}}.
 *
 * <ul>
 *   <li>{@code POST /api/webhooks/meta/deauth} — Facebook deauthorization callback.
 *       Called when a user removes the Meta/Facebook app from their account.
 *       No action needed beyond acknowledgement; we log the Meta user_id.</li>
 *   <li>{@code POST /api/webhooks/meta/data-deletion} — Meta data-deletion callback.
 *       Called when a user requests their data be deleted through Meta's platform.
 *       Returns a JSON confirmation code and a status URL as required by Meta.</li>
 * </ul>
 *
 * Both endpoints authenticate using Meta's {@code signed_request} form parameter:
 * {@code base64url(HMAC-SHA256(appSecret, base64url(payload))) + "." + base64url(payload)}.
 * The app secret comes from {@code konvo.whatsapp.meta.app-secret}.
 */
@RestController
@RequestMapping("/api/webhooks/meta")
@EnableConfigurationProperties(MetaProperties.class)
public class MetaAppCallbackController {

    private static final Logger log = LoggerFactory.getLogger(MetaAppCallbackController.class);

    private final MetaProperties props;
    private final ObjectMapper json;
    private final String appBaseUrl;

    public MetaAppCallbackController(
            MetaProperties props,
            ObjectMapper json,
            @Value("${konvo.public-base-url.app}") String appBaseUrl) {
        this.props = props;
        this.json = json;
        this.appBaseUrl = appBaseUrl.replaceAll("/$", "");
    }

    /**
     * Facebook deauthorization — called when a user removes the app.
     * Verifies the signed_request and logs the Meta user_id; returns 200.
     */
    @PostMapping(value = "/deauth", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> deauth(
            @RequestParam(name = "signed_request", required = false) String signedRequest) {
        String userId = verifySignedRequest(signedRequest);
        if (userId == null) {
            log.warn("Meta deauth: invalid or missing signed_request");
            return ResponseEntity.badRequest().build();
        }
        log.info("Meta deauth callback: userId={}", userId);
        return ResponseEntity.ok().build();
    }

    /**
     * Meta data-deletion callback — called when a user requests data removal.
     * Returns the JSON confirmation object Meta requires:
     * {@code {"url": "<statusUrl>", "confirmation_code": "<code>"}}.
     */
    @PostMapping(value = "/data-deletion", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Map<String, String>> dataDeletion(
            @RequestParam(name = "signed_request", required = false) String signedRequest) {
        String userId = verifySignedRequest(signedRequest);
        if (userId == null) {
            log.warn("Meta data-deletion: invalid or missing signed_request");
            return ResponseEntity.badRequest().build();
        }
        String code = UUID.randomUUID().toString().replace("-", "");
        log.info("Meta data-deletion callback: userId={} confirmationCode={}", userId, code);
        String statusUrl = appBaseUrl + "/data-deletion?code=" + code;
        return ResponseEntity.ok(Map.of("url", statusUrl, "confirmation_code", code));
    }

    /**
     * Parses and verifies a Meta signed_request. Returns the {@code user_id} from
     * the payload on success, or {@code null} if the request is absent, malformed,
     * or fails signature verification.
     */
    private String verifySignedRequest(String signedRequest) {
        if (signedRequest == null || signedRequest.isBlank()) return null;
        String appSecret = props.getAppSecret();
        if (appSecret == null || appSecret.isBlank()) {
            log.debug("Meta callback: app secret not configured — rejecting");
            return null;
        }
        try {
            String[] parts = signedRequest.split("\\.", 2);
            if (parts.length != 2) return null;
            String sigPart = parts[0];
            String dataPart = parts[1];

            byte[] expectedSig = MetaSignatureVerifier.computeBytes(
                    appSecret, dataPart.getBytes(StandardCharsets.UTF_8));
            byte[] presentedSig = Base64.getUrlDecoder().decode(padBase64Url(sigPart));
            if (!Arrays.equals(expectedSig, presentedSig)) {
                log.warn("Meta callback: signature mismatch");
                return null;
            }

            byte[] payloadBytes = Base64.getUrlDecoder().decode(padBase64Url(dataPart));
            JsonNode payload = json.readTree(payloadBytes);
            JsonNode userIdNode = payload.get("user_id");
            return (userIdNode != null && !userIdNode.isNull()) ? userIdNode.asText() : null;
        } catch (Exception e) {
            log.warn("Meta callback: error parsing signed_request — {}", e.getMessage());
            return null;
        }
    }

    private static String padBase64Url(String s) {
        return switch (s.length() % 4) {
            case 2 -> s + "==";
            case 3 -> s + "=";
            default -> s;
        };
    }
}
