package com.tally;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts a throwaway SQL Server in Docker for integration tests, the same engine as docker-compose.yml and
 * Azure SQL Database in production, and points the datasource at it ({@link ServiceConnection}).
 * Flyway then runs the real migrations, so tests see exactly the production schema.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    MSSQLServerContainer<?> sqlServerContainer() {
        return new MSSQLServerContainer<>(DockerImageName.parse("mcr.microsoft.com/mssql/server:2022-latest"))
                .acceptLicense();
    }
}
