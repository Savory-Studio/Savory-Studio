package com.apg.promociones.apg_api.exception;

/**
 * Thrown when a business rule prevents the operation.
 * Mapped to HTTP 409 Conflict by GlobalExceptionHandler.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
