package com.vulkantechtt.konvo.channels;

import com.vulkantechtt.konvo.common.BaseEntity;
import com.vulkantechtt.konvo.security.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "channels")
public class Channel extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 16)
    private ChannelProvider provider;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ChannelStatus status = ChannelStatus.connected;

    @Column(name = "phone_number", length = 32)
    private String phoneNumber;

    @Column(name = "phone_number_id", length = 64)
    private String phoneNumberId;

    @Column(name = "waba_id", length = 64)
    private String wabaId;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "app_secret", columnDefinition = "text")
    private String appSecret;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "access_token", columnDefinition = "text")
    private String accessToken;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "webhook_verify_token", nullable = false, columnDefinition = "text")
    private String webhookVerifyToken;

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public ChannelProvider getProvider() { return provider; }
    public void setProvider(ChannelProvider provider) { this.provider = provider; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public ChannelStatus getStatus() { return status; }
    public void setStatus(ChannelStatus status) { this.status = status; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getPhoneNumberId() { return phoneNumberId; }
    public void setPhoneNumberId(String phoneNumberId) { this.phoneNumberId = phoneNumberId; }

    public String getWabaId() { return wabaId; }
    public void setWabaId(String wabaId) { this.wabaId = wabaId; }

    public String getAppSecret() { return appSecret; }
    public void setAppSecret(String appSecret) { this.appSecret = appSecret; }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getWebhookVerifyToken() { return webhookVerifyToken; }
    public void setWebhookVerifyToken(String webhookVerifyToken) { this.webhookVerifyToken = webhookVerifyToken; }
}
