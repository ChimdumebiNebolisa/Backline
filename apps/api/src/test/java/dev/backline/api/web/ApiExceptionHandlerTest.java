package dev.backline.api.web;

import dev.backline.api.exception.ConflictException;
import dev.backline.api.exception.NotFoundException;
import dev.backline.api.exception.ValidationFailedException;
import dev.backline.core.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void mapsNotFoundTo404() {
        var response = handler.handleApiException(new NotFoundException("missing", "runId"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo(ErrorCode.NOT_FOUND.code());
        assertThat(response.getBody().error().field()).isEqualTo("runId");
    }

    @Test
    void mapsConflictTo409() {
        var response = handler.handleApiException(new ConflictException(ErrorCode.CONFLICT, "duplicate", "slug"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo(ErrorCode.CONFLICT.code());
    }

    @Test
    void mapsValidationFailureTo400() {
        var response = handler.handleApiException(new ValidationFailedException("bad field", "slug"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo(ErrorCode.VALIDATION_ERROR.code());
    }

    @Test
    void hidesUnexpectedExceptionsAs500() {
        var response = handler.handleGeneric(new IllegalStateException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo(ErrorCode.INTERNAL_ERROR.code());
    }
}
