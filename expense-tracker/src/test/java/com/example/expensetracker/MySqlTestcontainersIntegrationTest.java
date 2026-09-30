package com.example.expensetracker;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class MySqlTestcontainersIntegrationTest {

    @Container
    private static final MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("expensetrackertest")
        .withUsername("testuser")
        .withPassword("testpass");

    @BeforeAll
    static void checkDocker() {
        Assumptions.assumeTrue(
            isDockerRunning(),
            "Docker is not available on this environment - skipping Testcontainers MySQL integration test"
        );
    }

    private static boolean isDockerRunning() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @Test
    @DisplayName("Should connect to MySQL container and execute query when Docker is present")
    void testMySqlContainerConnection() throws Exception {
        Assumptions.assumeTrue(mysqlContainer.isRunning(), "MySQL container is not running");

        try (Connection connection = DriverManager.getConnection(
            mysqlContainer.getJdbcUrl(),
            mysqlContainer.getUsername(),
            mysqlContainer.getPassword()
        )) {
            assertNotNull(connection);
            try (Statement statement = connection.createStatement()) {
                ResultSet rs = statement.executeQuery("SELECT 1");
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            }
        }
    }
}
