package com.vulkantechtt.konvo.auth;

public record GoogleProfile(
        String subject,
        String email,
        String fullName,
        String pictureUrl) {
}
