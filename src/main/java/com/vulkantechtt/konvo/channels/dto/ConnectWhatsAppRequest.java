package com.vulkantechtt.konvo.channels.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ConnectWhatsAppRequest(
        @NotBlank @Size(max = 120) String displayName,
        @NotBlank @Pattern(regexp = "\\+?[0-9]{6,20}", message = "Phone number must be E.164-ish digits") String phoneNumber,
        @NotBlank @Size(max = 64) String phoneNumberId,
        @NotBlank @Size(max = 64) String wabaId,
        @NotBlank @Size(max = 255) String appSecret,
        @NotBlank String accessToken) {
}
