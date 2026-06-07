package com.vulkantechtt.konvo.whatsapp.meta;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class MetaWebhookPayloadTest {

    private final ObjectMapper json = JsonMapper.builder().build();

    @Test
    void deserializesStatusErrorsArray() throws Exception {
        String raw = """
                {
                  "object": "whatsapp_business_account",
                  "entry": [
                    {
                      "id": "waba",
                      "changes": [
                        {
                          "field": "messages",
                          "value": {
                            "statuses": [
                              {
                                "id": "wamid.out",
                                "status": "failed",
                                "timestamp": "1700000004",
                                "recipient_id": "18681112222",
                                "errors": [
                                  {
                                    "code": 131014,
                                    "title": "Request for url failed",
                                    "message": "Media could not be fetched."
                                  }
                                ]
                              }
                            ]
                          }
                        }
                      ]
                    }
                  ]
                }
                """;

        MetaWebhookPayload payload = json.readValue(raw, MetaWebhookPayload.class);

        MetaWebhookPayload.StatusUpdate status = firstStatus(payload);
        assertThat(status.errors()).hasSize(1);
        assertThat(status.errors().getFirst().code()).isEqualTo("131014");
        assertThat(status.errors().getFirst().title()).isEqualTo("Request for url failed");
        assertThat(status.errors().getFirst().message()).isEqualTo("Media could not be fetched.");
    }

    @Test
    void deserializesStatusErrorsSingleObject() throws Exception {
        String raw = """
                {
                  "object": "whatsapp_business_account",
                  "entry": [
                    {
                      "id": "waba",
                      "changes": [
                        {
                          "field": "messages",
                          "value": {
                            "statuses": [
                              {
                                "id": "wamid.out",
                                "status": "failed",
                                "timestamp": "1700000004",
                                "recipient_id": "18681112222",
                                "errors": {
                                  "code": "131000",
                                  "title": "Message failed",
                                  "message": "Provider rejected it"
                                }
                              }
                            ]
                          }
                        }
                      ]
                    }
                  ]
                }
                """;

        MetaWebhookPayload payload = json.readValue(raw, MetaWebhookPayload.class);

        MetaWebhookPayload.StatusUpdate status = firstStatus(payload);
        assertThat(status.errors()).hasSize(1);
        assertThat(status.errors().getFirst().code()).isEqualTo("131000");
        assertThat(status.errors().getFirst().title()).isEqualTo("Message failed");
        assertThat(status.errors().getFirst().message()).isEqualTo("Provider rejected it");
    }

    private static MetaWebhookPayload.StatusUpdate firstStatus(MetaWebhookPayload payload) {
        return payload.entry().getFirst()
                .changes().getFirst()
                .value()
                .statuses().getFirst();
    }
}
