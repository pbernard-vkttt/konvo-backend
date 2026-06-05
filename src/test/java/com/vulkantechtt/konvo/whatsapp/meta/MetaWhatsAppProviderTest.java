package com.vulkantechtt.konvo.whatsapp.meta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.vulkantechtt.konvo.channels.Channel;
import com.vulkantechtt.konvo.channels.ChannelRepository;
import com.vulkantechtt.konvo.common.KonvoException;
import com.vulkantechtt.konvo.whatsapp.WhatsAppProvider;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class MetaWhatsAppProviderTest {

    @Mock ChannelRepository channels;

    @Test
    void createTemplateAcceptsMetaJsonWithTextJavascriptContentType() {
        MetaProperties props = new MetaProperties();
        props.setGraphBaseUrl("https://graph.test");

        RestClient.Builder httpBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(httpBuilder).build();
        MetaWhatsAppProvider provider = new MetaWhatsAppProvider(
                props,
                channels,
                new ObjectMapper(),
                httpBuilder);

        UUID channelId = UUID.randomUUID();
        Channel channel = new Channel();
        channel.setId(channelId);
        channel.setWabaId("waba-123");
        channel.setAccessToken("meta-token");
        when(channels.findById(channelId)).thenReturn(Optional.of(channel));

        server.expect(requestTo("https://graph.test/v21.0/waba-123/message_templates"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer meta-token"))
                .andRespond(withSuccess(
                        "{\"id\":\"meta-42\",\"status\":\"PENDING\",\"category\":\"MARKETING\"}",
                        MediaType.valueOf("text/javascript;charset=UTF-8")));

        WhatsAppProvider.CreateTemplateResult result = provider.createTemplate(
                new WhatsAppProvider.CreateTemplateCommand(
                        channelId,
                        "promo_offer",
                        "en_US",
                        "MARKETING",
                        List.of()));

        assertThat(result.metaTemplateId()).isEqualTo("meta-42");
        assertThat(result.status()).isEqualTo("PENDING");
        server.verify();
    }

    @Test
    void createTemplateSurfacesFriendlyMessageWhenLanguageContentAlreadyExists() {
        MetaProperties props = new MetaProperties();
        props.setGraphBaseUrl("https://graph.test");

        RestClient.Builder httpBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(httpBuilder).build();
        MetaWhatsAppProvider provider = new MetaWhatsAppProvider(
                props,
                channels,
                new ObjectMapper(),
                httpBuilder);

        UUID channelId = UUID.randomUUID();
        Channel channel = new Channel();
        channel.setId(channelId);
        channel.setWabaId("waba-123");
        channel.setAccessToken("meta-token");
        when(channels.findById(channelId)).thenReturn(Optional.of(channel));

        // Real Meta 400 for subcode 2388024 — error.message is the useless
        // "Invalid parameter"; the detail lives in the error_user_* fields.
        server.expect(requestTo("https://graph.test/v21.0/waba-123/message_templates"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"Invalid parameter\","
                                + "\"type\":\"OAuthException\",\"code\":100,"
                                + "\"error_subcode\":2388024,"
                                + "\"error_user_title\":\"Content in This Language Already Exists\","
                                + "\"error_user_msg\":\"There is already English (US) content for this template.\"}}"));

        assertThatThrownBy(() -> provider.createTemplate(
                new WhatsAppProvider.CreateTemplateCommand(
                        channelId, "one_week_follow_up", "en_US", "MARKETING", List.of())))
                .isInstanceOf(KonvoException.class)
                .hasMessageContaining("already exists in this language")
                .hasMessageContaining("Sync from Meta");
        server.verify();
    }

    @Test
    void createTemplateSurfacesMetaUserMessageForOtherRejections() {
        MetaProperties props = new MetaProperties();
        props.setGraphBaseUrl("https://graph.test");

        RestClient.Builder httpBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(httpBuilder).build();
        MetaWhatsAppProvider provider = new MetaWhatsAppProvider(
                props,
                channels,
                new ObjectMapper(),
                httpBuilder);

        UUID channelId = UUID.randomUUID();
        Channel channel = new Channel();
        channel.setId(channelId);
        channel.setWabaId("waba-123");
        channel.setAccessToken("meta-token");
        when(channels.findById(channelId)).thenReturn(Optional.of(channel));

        server.expect(requestTo("https://graph.test/v21.0/waba-123/message_templates"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"Invalid parameter\","
                                + "\"error_user_title\":\"Invalid Format\","
                                + "\"error_user_msg\":\"Body text exceeds the character limit.\"}}"));

        assertThatThrownBy(() -> provider.createTemplate(
                new WhatsAppProvider.CreateTemplateCommand(
                        channelId, "promo_offer", "en_US", "MARKETING", List.of())))
                .isInstanceOf(KonvoException.class)
                .hasMessageContaining("Invalid Format")
                .hasMessageContaining("Body text exceeds the character limit");
        server.verify();
    }
}
