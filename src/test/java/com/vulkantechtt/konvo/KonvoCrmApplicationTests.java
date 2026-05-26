package com.vulkantechtt.konvo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Smoke test: confirms the Spring context loads against a real Postgres
 * (pgvector image) and that Flyway successfully applies all migrations.
 * RabbitMQ remains autoconfig-excluded — the messaging layer is exercised
 * by its own slice tests as M3+ adds queues.
 */
@SpringBootTest(classes = KonvoCrmApplication.class)
@EnableAutoConfiguration(exclude = {
        org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration.class
})
@ActiveProfiles("test")
@Testcontainers
class KonvoCrmApplicationTests {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17")
                    .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("konvo_test")
            .withUsername("konvo")
            .withPassword("konvo");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    ApplicationContext context;

    @Test
    void contextLoads() {
        // Bean wiring is the assertion.
    }
}
