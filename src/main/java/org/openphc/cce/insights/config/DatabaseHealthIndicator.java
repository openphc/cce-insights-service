package org.openphc.cce.insights.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

@Component
public class DatabaseHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;

    public DatabaseHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Health health() {
        try (Connection conn = dataSource.getConnection()) {
            if (conn.isValid(3)) {
                return Health.up()
                        .withDetail("database", "ClickHouse")
                        .withDetail("connection", "valid")
                        .build();
            }
            return Health.down()
                    .withDetail("database", "ClickHouse")
                    .withDetail("connection", "invalid")
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("database", "ClickHouse")
                    .withException(e)
                    .build();
        }
    }
}
