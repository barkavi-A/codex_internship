package com.example.expensetracker;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class FlywayMigrationTest {

    @Autowired
    private Flyway flyway;

    @Test
    @DisplayName("Should ensure Flyway migrations apply cleanly and all scripts are resolved")
    void testFlywayMigrationsApplyCleanly() {
        assertNotNull(flyway);
        MigrationInfo[] appliedMigrations = flyway.info().applied();

        assertTrue(appliedMigrations.length >= 2, "Expected at least V1 and V2 migrations to have run");

        MigrationInfo v1 = appliedMigrations[0];
        assertEquals("1", v1.getVersion().getVersion());
        assertEquals("init schema", v1.getDescription());

        MigrationInfo v2 = appliedMigrations[1];
        assertEquals("2", v2.getVersion().getVersion());
        assertEquals("seed default categories", v2.getDescription());
    }
}
