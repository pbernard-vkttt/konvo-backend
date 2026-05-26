package com.vulkantechtt.konvo.users.dto;

import com.vulkantechtt.konvo.users.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InviteMemberRequest(
        @NotBlank @Email String email,
        @NotNull Role role) {
}
