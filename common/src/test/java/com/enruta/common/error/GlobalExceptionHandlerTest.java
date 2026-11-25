package com.enruta.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void businessExceptionKeepsItsCodeAndStatus() {
        ProblemDetail problem =
                handler.handleBusiness(BusinessException.conflict("OFFER_EXPIRED", "ya no"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getProperties()).containsEntry("code", "OFFER_EXPIRED");
        assertThat(problem.getDetail()).isEqualTo("ya no");
    }

    @Test
    void serverSideBusinessExceptionIsLoggedAsError() {
        ProblemDetail problem =
                handler.handleBusiness(
                        new BusinessException(
                                "DEPENDENCY_DOWN",
                                "no disponible",
                                HttpStatus.SERVICE_UNAVAILABLE));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(problem.getProperties()).containsEntry("code", "DEPENDENCY_DOWN");
    }

    @Test
    void invalidBodyBecomesValidationError() {
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        BindingResult binding = mock(BindingResult.class);
        when(exception.getBindingResult()).thenReturn(binding);
        when(binding.getFieldErrors()).thenReturn(List.of());

        ProblemDetail problem = handler.handleInvalidBody(exception);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getProperties()).containsEntry("code", "VALIDATION_ERROR");
    }

    @Test
    void constraintViolationsAreClientErrors() {
        ProblemDetail problem =
                handler.handleConstraint(new ConstraintViolationException(Set.of()));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getProperties()).containsEntry("code", "VALIDATION_ERROR");
    }

    @Test
    void malformedUuidIsABadRequestNotAServerError() {
        MethodArgumentTypeMismatchException exception =
                mock(MethodArgumentTypeMismatchException.class);
        when(exception.getName()).thenReturn("id");
        when(exception.getValue()).thenReturn("no-es-uuid");

        ProblemDetail problem = handler.handleTypeMismatch(exception);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getDetail()).contains("id").contains("no-es-uuid");
    }

    @Test
    void unexpectedErrorsDoNotLeakInternals() {
        ProblemDetail problem =
                handler.handleUnexpected(new IllegalStateException("password=secreto"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getDetail()).doesNotContain("secreto");
    }
}
