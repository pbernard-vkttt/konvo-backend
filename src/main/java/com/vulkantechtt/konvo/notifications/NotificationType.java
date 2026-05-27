package com.vulkantechtt.konvo.notifications;

public enum NotificationType {
    CONVERSATION_ASSIGNED("conversation.assigned"),
    MEMBER_JOINED("member.joined"),
    AI_QUOTA_PAUSED("ai.quota.paused");

    private final String code;

    NotificationType(String code) { this.code = code; }

    public String code() { return code; }
}
