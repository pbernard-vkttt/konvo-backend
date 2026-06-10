package com.vulkantechtt.konvo.conversations;

/**
 * Who authored a message. Inbound messages are always from the {@code customer};
 * outbound replies are either typed by a human {@code agent} or drafted by the
 * {@code ai} (Vee). Drives the sender badge shown on reply bubbles in the inbox.
 */
public enum SenderType {
    customer,
    agent,
    ai
}
