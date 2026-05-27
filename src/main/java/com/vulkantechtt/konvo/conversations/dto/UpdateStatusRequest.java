package com.vulkantechtt.konvo.conversations.dto;

import com.vulkantechtt.konvo.conversations.ConversationStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(@NotNull ConversationStatus status) {}
