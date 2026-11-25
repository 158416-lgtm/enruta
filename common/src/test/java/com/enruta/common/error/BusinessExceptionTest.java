package com.enruta.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class BusinessExceptionTest {

    @Test
    void factoriesAssignTheHttpStatus() {
        assertThat(BusinessException.conflict("A", "a").getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(BusinessException.notFound("B", "b").getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(BusinessException.badRequest("C", "c").getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void problemDetailCarriesTheStableCode() {
        BusinessException error = BusinessException.notFound("USER_NOT_FOUND", "no existe");

        assertThat(error.getCode()).isEqualTo("USER_NOT_FOUND");
        assertThat(error.getMessage()).isEqualTo("no existe");
        assertThat(error.toProblemDetail().getProperties()).containsEntry("code", "USER_NOT_FOUND");
    }
}
