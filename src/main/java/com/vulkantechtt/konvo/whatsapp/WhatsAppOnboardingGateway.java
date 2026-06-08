package com.vulkantechtt.konvo.whatsapp;

/**
 * Completes a Meta WhatsApp <em>Embedded Signup</em> handshake server-side.
 *
 * <p>The browser runs Meta's Embedded Signup flow and comes back with three
 * things: a short-lived OAuth {@code code}, the {@code phone_number_id} and the
 * {@code waba_id} the customer just provisioned. The code must be exchanged for
 * a business access token using the app secret — which can never reach the
 * browser — so the exchange and the follow-up Graph calls happen here.
 *
 * <p>Implemented by the Meta adapter only; absent when the WhatsApp provider is
 * the stub, so callers must treat the gateway as optional.
 */
public interface WhatsAppOnboardingGateway {

    /**
     * Exchanges the Embedded Signup {@code code} for a long-lived business access
     * token, reads the provisioned number's display details, and subscribes our
     * app to the customer's WABA so inbound webhooks start flowing.
     *
     * @param code          OAuth authorization code from the Embedded Signup callback
     * @param phoneNumberId WhatsApp phone number id reported by the signup session
     * @param wabaId        WhatsApp Business Account id reported by the signup session
     * @return everything {@code channels} needs to persist a connected channel
     */
    OnboardingResult completeSignup(String code, String phoneNumberId, String wabaId);

    /**
     * Result of a completed Embedded Signup exchange.
     *
     * @param accessToken        business access token for Cloud API calls on this number
     * @param appSecret          app secret used to verify this channel's webhook signatures
     * @param displayPhoneNumber the number in E.164-ish digits (e.g. {@code +18681234567})
     * @param verifiedName       Meta-verified business display name, or {@code null}
     */
    record OnboardingResult(
            String accessToken,
            String appSecret,
            String displayPhoneNumber,
            String verifiedName) {}
}
