package org.openphc.cce.insights.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DatabaseHealthIndicator} — reports UP when the ClickHouse connection
 * validates, DOWN when it does not, and DOWN (with the exception recorded) when acquiring the
 * connection fails.
 */
@ExtendWith(MockitoExtension.class)
class DatabaseHealthIndicatorTest {

    @Mock private DataSource dataSource;
    @Mock private Connection connection;

    @Test
    void health_whenConnectionValid_returnsUp() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(3)).thenReturn(true);

        Health health = new DatabaseHealthIndicator(dataSource).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("database", "ClickHouse")
                .containsEntry("connection", "valid");
    }

    @Test
    void health_whenConnectionInvalid_returnsDown() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(3)).thenReturn(false);

        Health health = new DatabaseHealthIndicator(dataSource).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("connection", "invalid");
    }

    @Test
    void health_whenGetConnectionThrows_returnsDownWithException() throws SQLException {
        when(dataSource.getConnection()).thenThrow(new SQLException("connection refused"));

        Health health = new DatabaseHealthIndicator(dataSource).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("error");
    }
}
