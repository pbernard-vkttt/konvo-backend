package com.vulkantechtt.konvo.whatsapp.meta;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.List;

/**
 * Subset of the Meta webhook envelope we actually consume. Boot 4's
 * autoconfigured Jackson mapper has {@code FAIL_ON_UNKNOWN_PROPERTIES=false},
 * so any extra fields Meta sends (and there are many) are silently dropped.
 *
 * Reference shape (Cloud API v21, "field":"messages" change):
 *   {
 *     "object":"whatsapp_business_account",
 *     "entry":[{
 *       "id":"<waba_id>",
 *       "changes":[{
 *         "field":"messages",
 *         "value":{
 *           "messaging_product":"whatsapp",
 *           "metadata":{"display_phone_number":"...","phone_number_id":"..."},
 *           "contacts":[{"profile":{"name":"..."},"wa_id":"..."}],
 *           "messages":[{"from":"...","id":"wamid...","timestamp":"...","type":"text","text":{"body":"..."}}]
 *         }
 *       }]
 *     }]
 *   }
 */
public record MetaWebhookPayload(String object, List<Entry> entry) {

    public record Entry(String id, List<Change> changes) {}

    public record Change(String field, Value value) {}

    public record Value(
            Metadata metadata,
            List<Contact> contacts,
            List<InboundMessage> messages,
            List<StatusUpdate> statuses) {}

    public record Metadata(String display_phone_number, String phone_number_id) {}

    public record Contact(Profile profile, String wa_id) {}

    public record Profile(String name) {}

    public record InboundMessage(
            String from,
            String id,
            String timestamp,
            String type,
            TextBody text) {}

    public record TextBody(String body) {}

    public record StatusUpdate(
            String id,
            String status,
            String timestamp,
            String recipient_id,
            @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            List<ErrorDetail> errors) {}

    public record ErrorDetail(String code, String title, String message) {}
}
