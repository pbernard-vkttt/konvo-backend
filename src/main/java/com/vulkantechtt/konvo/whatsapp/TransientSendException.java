package com.vulkantechtt.konvo.whatsapp;

/**
 * Thrown by a {@link WhatsAppProvider} when a send fails for a reason that is
 * worth retrying — network/transport errors, HTTP 429 (rate limited), or 5xx
 * responses from the provider. Permanent failures (rejected credentials, bad
 * request, unknown channel) are signalled with a normal
 * {@link com.vulkantechtt.konvo.common.KonvoException} instead, so callers can
 * stop immediately rather than burning retry attempts.
 *
 * @see com.vulkantechtt.konvo.conversations.OutboundSendListener
 */
public class TransientSendException extends RuntimeException {

    public TransientSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
