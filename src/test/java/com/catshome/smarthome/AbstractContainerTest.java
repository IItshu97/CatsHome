package com.catshome.smarthome;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared base for all tests that need a real PostgreSQL and InfluxDB.
 * Singleton container pattern: both containers start once per JVM.
 * Testcontainers registers JVM shutdown hooks to clean them up.
 */
public abstract class AbstractContainerTest {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @SuppressWarnings("resource")
    public static final GenericContainer<?> INFLUXDB =
            new GenericContainer<>(DockerImageName.parse("influxdb:2.7-alpine"))
                    .withExposedPorts(8086)
                    .withEnv("DOCKER_INFLUXDB_INIT_MODE", "setup")
                    .withEnv("DOCKER_INFLUXDB_INIT_USERNAME", "admin")
                    .withEnv("DOCKER_INFLUXDB_INIT_PASSWORD", "adminpassword")
                    .withEnv("DOCKER_INFLUXDB_INIT_ORG", "catshome")
                    .withEnv("DOCKER_INFLUXDB_INIT_BUCKET", "smarthome")
                    .withEnv("DOCKER_INFLUXDB_INIT_ADMIN_TOKEN", "test-admin-token");

    static {
        POSTGRES.start();
        INFLUXDB.start();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("influxdb.url", () ->
                "http://" + INFLUXDB.getHost() + ":" + INFLUXDB.getMappedPort(8086));
        registry.add("influxdb.token", () -> "test-admin-token");
        registry.add("influxdb.org",   () -> "catshome");
        registry.add("influxdb.bucket", () -> "smarthome");
    }
}