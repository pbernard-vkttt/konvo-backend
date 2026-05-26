package com.vulkantechtt.konvo.auth.dto;

import com.vulkantechtt.konvo.users.Role;

public record InvitationPreviewResponse(
        String email,
        String workspaceName,
        Role role) {
}
