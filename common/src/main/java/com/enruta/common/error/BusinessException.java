package com.enruta.common.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * Excepcion de negocio con codigo estable.
 *
 * <p>El codigo ({@code OFFER_EXPIRED}, {@code INVALID_TRANSITION}, ...) es parte del contrato
 * publico de la API: el frontend decide con el codigo, no con el texto.
 */
public class BusinessException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public BusinessException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public static BusinessException conflict(String code, String message) {
        return new BusinessException(code, message, HttpStatus.CONFLICT);
    }

    public static BusinessException notFound(String code, String message) {
        return new BusinessException(code, message, HttpStatus.NOT_FOUND);
    }

    public static BusinessException badRequest(String code, String message) {
        return new BusinessException(code, message, HttpStatus.BAD_REQUEST);
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public ProblemDetail toProblemDetail() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, getMessage());
        problem.setTitle(code);
        problem.setProperty("code", code);
        return problem;
    }
}
