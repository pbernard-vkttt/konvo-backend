package com.vulkantechtt.konvo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Smoke test: confirms the Spring context loads with the M1 bean graph.
 * Disables datasource/JPA/Flyway/RabbitMQ auto-config so the test runs in CI
 * without external services. The integration test suite (M2+) will use
 * Testcontainers to spin up the real dependencies.
 */
@SpringBootTest(classes = KonvoCrmApplication.class)
@EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration," +
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
        "spring.jpa.repositories.enabled=false"
})
class KonvoCrmApplicationTests {

    @Test
    void contextLoads() {
        // Bean wiring is the assertion.
    }
}
