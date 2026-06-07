package com.vulkantechtt.konvo.billing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePlanRequest(
        @NotBlank
        @Size(max = 32)
        String planId) {
}
