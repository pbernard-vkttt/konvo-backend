package com.vulkantechtt.konvo.channels.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** PATCH body for editing a channel — credential fields are optional so the
 *  user can change just the display name without re-entering the token. */
public record UpdateChannelRequest(
        @NotBlank @Size(max = 120) String displayName,
        String accessToken,
        String appSecret) {
}
