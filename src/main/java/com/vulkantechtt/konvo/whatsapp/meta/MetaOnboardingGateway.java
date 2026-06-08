package com.vulkantechtt.konvo.whatsapp.meta;

import com.vulkantechtt.konvo.common.KonvoException;
import com.vulkantechtt.konvo.whatsapp.WhatsAppOnboardingGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Meta adapter for WhatsApp Embedded Signup. Active when
 * {@code konvo.whatsapp.provider=meta} — the stub provider has no real Graph API
 * to talk to, so the gateway is simply absent there.
 *
 * <p>Three Graph calls complete a signup (Graph version is pinned by
 * {@link MetaProperties}):
 * <ol>
 *   <li>{@code GET /oauth/access_token} — swap the OAuth {@code code} for a
 *       business access token using the app id + app secret.</li>
 *   <li>{@code GET /{phone_number_id}?fields=display_phone_number,verified_name}
 *       — read the number's E.164 form and Meta-verified business name.</li>
 *   <li>{@code POST /{waba_id}/subscribed_apps} — subscribe our app to the
 *       customer's WABA so inbound message webhooks start flowing.</li>
 * </ol>
 */
@Component
@ConditionalOnProperty(prefix = "konvo.whatsapp", name = "provider", havingValue = "meta")
@EnableConfigurationProperties(MetaProperties.class)
public class MetaOnboardingGateway implements WhatsAppOnboardingGateway {

    private static final Logger log = LoggerFactory.getLogger(MetaOnboardingGateway.class);

    private final MetaProperties props;
    private final RestClient http;
    private final ObjectMapper json;

    @Autowired
    public MetaOnboardingGateway(MetaProperties props, ObjectMapper json) {
        this(props, json, RestClient.builder());
    }

    MetaOnboardingGateway(MetaProperties props, ObjectMapper json, RestClient.Builder httpBuilder) {
        this.props = props;
        this.json = json;
        this.http = httpBuilder.baseUrl(props.getGraphBaseUrl()).build();
    }

    @Override
    public OnboardingResult completeSignup(String code, String phoneNumberId, String wabaId) {
        String appId = props.getAppId();
        String appSecret = props.getAppSecret();
        if (appId == null || appId.isBlank() || appSecret == null || appSecret.isBlank()) {
            throw new KonvoException(HttpStatus.SERVICE_UNAVAILABLE, "embedded_signup_unconfigured",
                    "WhatsApp Embedded Signup is not configured on this server");
        }

        String accessToken = exchangeCode(code, appId, appSecret);
        PhoneDetails phone = readPhoneDetails(phoneNumberId, accessToken);
        subscribeApp(wabaId, accessToken);

        return new OnboardingResult(accessToken, appSecret, phone.normalizedNumber(), phone.cleanVerifiedName());
    }

    /** Swap the Embedded Signup OAuth code for a business access token. */
    private String exchangeCode(String code, String appId, String appSecret) {
        String version = props.getGraphApiVersion();
        try {
            String resp = http.get()
                    .uri(uri -> uri.path("/" + version + "/oauth/access_token")
                            .queryParam("client_id", appId)
                            .queryParam("client_secret", appSecret)
                            .queryParam("code", code)
                            .build())
                    .retrieve()
                    .body(String.class);
            JsonNode node = readTree(resp);
            String token = text(node, "access_token");
            if (token == null) {
                log.warn("Embedded signup: token exchange returned no access_token");
                throw new KonvoException(HttpStatus.BAD_GATEWAY, "embedded_signup_failed",
                        "Meta did not return an access token for this signup");
            }
            return token;
        } catch (RestClientResponseException e) {
            log.warn("Embedded signup: token exchange failed status={} body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw badRequestFrom(e, "Meta rejected this signup. Please try connecting again.");
        } catch (RestClientException e) {
            log.warn("Embedded signup: token exchange transport error: {}", e.toString());
            throw new KonvoException(HttpStatus.BAD_GATEWAY, "embedded_signup_failed",
                    "Could not reach Meta to complete the signup");
        }
    }

    /** Read the provisioned number's display phone + verified business name. */
    private PhoneDetails readPhoneDetails(String phoneNumberId, String accessToken) {
        String version = props.getGraphApiVersion();
        try {
            String resp = http.get()
                    .uri(uri -> uri.path("/" + version + "/" + phoneNumberId)
                            .queryParam("fields", "display_phone_number,verified_name")
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(String.class);
            JsonNode node = readTree(resp);
            return new PhoneDetails(text(node, "display_phone_number"), text(node, "verified_name"));
        } catch (RestClientResponseException e) {
            log.warn("Embedded signup: phone lookup failed status={} body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw badRequestFrom(e, "Could not read the WhatsApp number details from Meta");
        } catch (RestClientException e) {
            log.warn("Embedded signup: phone lookup transport error: {}", e.toString());
            throw new KonvoException(HttpStatus.BAD_GATEWAY, "embedded_signup_failed",
                    "Could not reach Meta to read the number details");
        }
    }

    /** Subscribe our app to the customer's WABA so inbound webhooks arrive. */
    private void subscribeApp(String wabaId, String accessToken) {
        String version = props.getGraphApiVersion();
        try {
            http.post()
                    .uri("/" + version + "/" + wabaId + "/subscribed_apps")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            log.warn("Embedded signup: subscribe_apps failed status={} body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw badRequestFrom(e, "Could not subscribe Konvelo to this WhatsApp account");
        } catch (RestClientException e) {
            log.warn("Embedded signup: subscribe_apps transport error: {}", e.toString());
            throw new KonvoException(HttpStatus.BAD_GATEWAY, "embedded_signup_failed",
                    "Could not reach Meta to subscribe to this WhatsApp account");
        }
    }

    private record PhoneDetails(String displayPhoneNumber, String verifiedName) {
        /** Reduce Meta's spaced/punctuated number to {@code +<digits>}. */
        String normalizedNumber() {
            if (displayPhoneNumber == null) {
                return null;
            }
            String digits = displayPhoneNumber.replaceAll("[^0-9]", "");
            return digits.isEmpty() ? null : "+" + digits;
        }

        String cleanVerifiedName() {
            return verifiedName != null && !verifiedName.isBlank() ? verifiedName : null;
        }
    }

    private JsonNode readTree(String body) {
        if (body == null || body.isBlank()) {
            throw new KonvoException(HttpStatus.BAD_GATEWAY, "embedded_signup_failed",
                    "Meta returned an empty response");
        }
        try {
            return json.readTree(body);
        } catch (Exception e) {
            throw new KonvoException(HttpStatus.BAD_GATEWAY, "embedded_signup_failed",
                    "Could not decode Meta's response");
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isTextual() && !v.asText().isBlank() ? v.asText() : null;
    }

    /** Prefer Meta's human-facing error text for 4xx; fall back to a 502. */
    private KonvoException badRequestFrom(RestClientResponseException e, String fallback) {
        int status = e.getStatusCode().value();
        if (status >= 400 && status < 500) {
            return KonvoException.badRequest(metaErrorMessage(e.getResponseBodyAsString(), fallback));
        }
        return new KonvoException(HttpStatus.BAD_GATEWAY, "embedded_signup_failed", fallback);
    }

    private String metaErrorMessage(String body, String fallback) {
        try {
            JsonNode error = json.readTree(body).path("error");
            String userMsg = text(error, "error_user_msg");
            if (userMsg != null) {
                String userTitle = text(error, "error_user_title");
                return userTitle != null ? userTitle + ": " + userMsg : userMsg;
            }
            String message = text(error, "message");
            if (message != null) {
                return message;
            }
        } catch (Exception ignored) {
            // fall through
        }
        return fallback;
    }
}
