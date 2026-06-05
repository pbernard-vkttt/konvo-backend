package com.vulkantechtt.konvo.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Import a web page into the knowledge base by URL. Title is optional — when
 *  blank we fall back to the page's host. */
public record CreateUrlSourceRequest(
        @Size(max = 200) String title,
        @NotBlank @Size(max = 2000) String url) {
}
