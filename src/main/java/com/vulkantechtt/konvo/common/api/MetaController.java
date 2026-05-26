package com.vulkantechtt.konvo.common.api;

import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simple endpoint to confirm the API is reachable and to surface non-secret
 * configuration. Useful while we don't yet have a real domain endpoint to call.
 */
@RestController
@RequestMapping("/api/v1/meta")
public class MetaController {

    @Value("${spring.application.name}")
    private String appName;

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    @GetMapping
    public Map<String, Object> meta() {
        return Map.of(
                "service", appName,
                "profile", activeProfile,
                "now", Instant.now().toString(),
                "milestone", "M1");
    }
}
