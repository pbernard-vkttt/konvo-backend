package com.vulkantechtt.konvo.scheduling;

import com.vulkantechtt.konvo.scheduling.google.SchedulingGoogleProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SchedulingGoogleProperties.class)
public class SchedulingConfig {
}
