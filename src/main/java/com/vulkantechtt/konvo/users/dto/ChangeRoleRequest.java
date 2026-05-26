package com.vulkantechtt.konvo.users.dto;

import com.vulkantechtt.konvo.users.Role;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(@NotNull Role role) {}
