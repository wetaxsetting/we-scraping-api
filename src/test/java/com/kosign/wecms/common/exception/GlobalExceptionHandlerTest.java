package com.kosign.wecms.common.exception;

import com.kosign.wecms.common.i18n.MessageResolver;
import com.kosign.wecms.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the {@code WBE_VALIDATION} envelope mappings for the four
 * validation-shaped exceptions Spring raises:
 * <ul>
 *   <li>{@code @Valid @RequestBody} → {@code MethodArgumentNotValidException}
 *       (already covered indirectly by integration in {@code AuthServiceTest})</li>
 *   <li>{@code @Validated} on path / param → {@code ConstraintViolationException}</li>
 *   <li><b>Required {@code @RequestParam} missing</b> →
 *       {@code MissingServletRequestParameterException} (this test)</li>
 *   <li>{@code @RequestParam} type-mismatch →
 *       {@code MethodArgumentTypeMismatchException} (this test)</li>
 * </ul>
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setup() {
        MessageResolver messages = mock(MessageResolver.class);
        when(messages.resolve("WBE_VALIDATION")).thenReturn("Invalid input.");
        when(messages.resolve("WBE9999")).thenReturn("An unexpected error occurred.");
        handler = new GlobalExceptionHandler(messages);
        request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/test");
        when(request.getMethod()).thenReturn("GET");
    }

    @Test
    void missingRequestParam_mapsToWbeValidation() {
        MissingServletRequestParameterException ex =
                new MissingServletRequestParameterException("from", "String");

        ResponseEntity<ApiResponse<Void>> resp = handler.handleMissingParam(ex, request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getStatus().isError()).isTrue();
        assertThat(resp.getBody().getStatus().getCode()).isEqualTo("WBE_VALIDATION");
        assertThat(resp.getBody().getStatus().getMessage()).contains("from", "is required");
    }

    @Test
    void typeMismatch_mapsToWbeValidation() throws NoSuchMethodException {
        // Build a minimal MethodArgumentTypeMismatchException — the
        // production exception fires when Spring fails to convert
        // a request param to the target type (e.g. ?page=foo for int).
        Method method = SamplePoint.class.getDeclaredMethod("handle", int.class);
        MethodParameter param = new MethodParameter(method, 0);
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "foo", int.class, "page", param, new IllegalArgumentException("nope"));

        ResponseEntity<ApiResponse<Void>> resp = handler.handleTypeMismatch(ex, request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getStatus().isError()).isTrue();
        assertThat(resp.getBody().getStatus().getCode()).isEqualTo("WBE_VALIDATION");
        assertThat(resp.getBody().getStatus().getMessage()).contains("page", "invalid value");
    }

    /** Stub used only as a {@link MethodParameter} source in
     *  {@link #typeMismatch_mapsToWbeValidation()}. */
    @SuppressWarnings("unused")
    static class SamplePoint {
        void handle(int page) { }
    }
}
