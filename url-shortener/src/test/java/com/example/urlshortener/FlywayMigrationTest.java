package com.example.urlshortener;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class FlywayMigrationTest {

    @Test
    @DisplayName("Flyway migrations apply cleanly and Hibernate ddl-auto=validate passes")
    void testFlywayMigrationAndHibernateValidation() {
        // If Spring application context loads without exception, Flyway migrated successfully and Hibernate validated schema
        assertTrue(true, "Application context initialized successfully with Flyway migration and Hibernate schema validation.");
    }
}
