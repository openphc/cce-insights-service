package org.openphc.cce.insights.web;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.openphc.cce.insights.web.dto.ApiResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GlobalExceptionHandler} — maps exception types to the standard
 * {@link ApiResponse} error envelope and HTTP status, without leaking internal detail on 5xx.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleNotFound_returns404WithMessage() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleNotFound(new EntityNotFoundException("protocol 7 not found"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getError().getCode()).isEqualTo("NOT_FOUND");
        assertThat(response.getBody().getError().getMessage()).isEqualTo("protocol 7 not found");
    }

    @Test
    void handleBadRequest_returns400ValidationError() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleBadRequest(new IllegalArgumentException("bad interval"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getError().getCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().getError().getMessage()).isEqualTo("bad interval");
    }

    @Test
    void handleDatabaseError_returns503WithGenericMessage() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleDatabaseError(new DataAccessException("clickhouse boom") {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().getError().getCode()).isEqualTo("SERVICE_UNAVAILABLE");
        // Internal detail must not leak.
        assertThat(response.getBody().getError().getMessage()).isEqualTo("Database unavailable");
    }

    @Test
    void handleGenericError_returns500WithMaskedMessage() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleGenericError(new RuntimeException("stack trace with secrets"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getError().getCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().getError().getMessage()).isEqualTo("An unexpected error occurred");
    }
}
