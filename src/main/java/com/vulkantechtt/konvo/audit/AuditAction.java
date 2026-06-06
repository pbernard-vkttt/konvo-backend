package com.vulkantechtt.konvo.audit;

/**
 * Canonical action codes for {@link AuditLog#getAction()}. Stored as a string
 * so a new action lands without a migration; the enum is what code references.
 */
public enum AuditAction {
    MEMBER_INVITED("member.invited", "membership"),
    MEMBER_INVITATION_REVOKED("member.invitation.revoked", "invitation"),
    MEMBER_ROLE_CHANGED("member.role.changed", "membership"),
    MEMBER_REMOVED("member.removed", "membership"),
    MEMBER_JOINED("member.joined", "membership"),

    CHANNEL_CONNECTED("channel.connected", "channel"),
    CHANNEL_UPDATED("channel.updated", "channel"),
    CHANNEL_DISCONNECTED("channel.disconnected", "channel"),

    WORKSPACE_SETTINGS_UPDATED("workspace.settings.updated", "tenant"),

    TEMPLATE_CREATED("template.created", "template"),
    TEMPLATE_SYNCED("template.synced", "template"),
    TEMPLATE_SENT("template.sent", "template"),

    SUBSCRIPTION_PROVISIONED("subscription.provisioned", "subscription"),

    SCHEDULING_SETTINGS_UPDATED("scheduling.settings.updated", "scheduling"),
    SCHEDULING_GOOGLE_CONNECTED("scheduling.google.connected", "scheduling"),
    SCHEDULING_GOOGLE_DISCONNECTED("scheduling.google.disconnected", "scheduling"),
    APPOINTMENT_BOOKED("appointment.booked", "appointment"),
    APPOINTMENT_CANCELLED("appointment.cancelled", "appointment");

    private final String code;
    private final String entityType;

    AuditAction(String code, String entityType) {
        this.code = code;
        this.entityType = entityType;
    }

    public String code() { return code; }
    public String entityType() { return entityType; }
}
