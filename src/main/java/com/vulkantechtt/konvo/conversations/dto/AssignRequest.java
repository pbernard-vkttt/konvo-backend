package com.vulkantechtt.konvo.conversations.dto;

import java.util.UUID;

/** Pass {@code null} to unassign. */
public record AssignRequest(UUID assignedUserId) {}
