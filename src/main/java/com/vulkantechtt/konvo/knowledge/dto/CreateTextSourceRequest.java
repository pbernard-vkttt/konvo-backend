package com.vulkantechtt.konvo.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTextSourceRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 100_000) String content) {
}
