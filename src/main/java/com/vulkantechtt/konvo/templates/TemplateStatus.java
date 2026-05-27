package com.vulkantechtt.konvo.templates;

/** Meta's template lifecycle. {@code approved} is the only sendable state. */
public enum TemplateStatus {
    approved,
    pending,
    rejected,
    paused,
    disabled
}
