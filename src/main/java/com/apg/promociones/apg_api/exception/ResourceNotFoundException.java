package com.apg.promociones.apg_api.exception;

/**
 * Thrown when a requested resource does not exist.
 * Mapped to HTTP 404 Not Found by GlobalExceptionHandler.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
