package com.vulkantechtt.konvo.templates.dto;

import com.vulkantechtt.konvo.templates.TemplateCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Creates a new WhatsApp message template and submits it to Meta for review.
 *
 * Only the common text-based shape is supported here: optional text header,
 * required text body, optional footer. Variable examples are provided as
 * simple ordered lists and mapped to Meta's example payload format.
 */
public record CreateTemplateRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 16) String language,
        @NotNull TemplateCategory category,
        @Size(max = 60) String headerText,
        List<@NotBlank @Size(max = 200) String> headerExamples,
        @NotBlank @Size(max = 1024) String bodyText,
        List<@NotBlank @Size(max = 200) String> bodyExamples,
        @Size(max = 60) String footerText) {
}
