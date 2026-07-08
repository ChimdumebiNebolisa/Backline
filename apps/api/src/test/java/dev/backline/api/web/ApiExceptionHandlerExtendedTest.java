package dev.backline.api.web;

import dev.backline.core.error.ErrorCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiExceptionHandlerExtendedTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void handleMethodArgumentNotValid_returnsFieldError() throws Exception {
        var target = new Object();
        var binding = new BeanPropertyBindingResult(target, "req");
        binding.addError(new FieldError("req", "slug", "must not be blank"));
        var ex = new MethodArgumentNotValidException(
                new MethodParameter(String.class.getDeclaredMethod("toString"), -1), binding);

        var response = handler.handleMethodArgumentNotValid(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().field()).isEqualTo("slug");
    }

    @Test
    void handleMissingParam_returns400() {
        var ex = new MissingServletRequestParameterException("limit", "int");
        var response = handler.handleMissingParam(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error().code()).isEqualTo(ErrorCode.BAD_REQUEST.code());
    }

    @Test
    void handleTypeMismatch_returns400() {
        var ex = new MethodArgumentTypeMismatchException("bad", UUID.class, "runId", null, null);
        var response = handler.handleTypeMismatch(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void handleNotReadable_returns400() {
        var response = handler.handleNotReadable(new HttpMessageNotReadableException("bad json", null, null));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void handleMethodNotAllowed_returns405() {
        var response = handler.handleMethodNotAllowed(new HttpRequestMethodNotSupportedException("PATCH"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void handleUnsupportedMediaType_returns415() {
        var response = handler.handleUnsupportedMediaType(new HttpMediaTypeNotSupportedException("text/plain"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void handleConstraintViolation_returns400WithPath() {
        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("sync.checks[0].url");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("must be http or https");
        var ex = new ConstraintViolationException(Set.of(violation));
        var response = handler.handleConstraintViolation(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error().field()).contains("url");
    }
}
