package com.vulkantechtt.konvo.notifications.dto;

import java.util.List;

public record NotificationFeed(List<NotificationResponse> items, long unreadCount) {
}
