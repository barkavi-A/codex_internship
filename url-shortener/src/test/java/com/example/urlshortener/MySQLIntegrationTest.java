package com.example.urlshortener;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MySQLIntegrationTest {

    @Container
    private static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("test_db")
            .withUsername("test")
            .withPassword("test");

    @BeforeAll
    static void checkDockerAvailability() {
        boolean dockerAvailable = false;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (Exception e) {
            dockerAvailable = false;
        }
        assumeTrue(dockerAvailable, "Skipping Testcontainers MySQL integration test: Docker is not available in environment.");
    }

    @Test
    @DisplayName("Optional Testcontainers MySQL test runs when Docker is present")
    void testMySQLContainer() {
        mysql.start();
        assertTrue(mysql.isRunning(), "MySQL container should be running");
        mysql.stop();
    }
}
