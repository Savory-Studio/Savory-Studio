package com.apg.promociones.apg_api.exception;

import com.apg.promociones.apg_api.dto.ErrorResponse;

import com.apg.promociones.apg_api.exception.PromocionExceptions.PromocionDuplicadaException;

import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;
import org.springframework.dao.PessimisticLockingFailureException;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 400 Bad Request — custom validation errors (RN-06, RN-08, RN-09, etc.)
    @ExceptionHandler(BadRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadRequest(BadRequestException ex) {
        return new ErrorResponse(400, ex.getMessage());
    }

    // 401 Unauthorized — missing or invalid Bearer token
    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleUnauthorized(UnauthorizedException ex) {
        return new ErrorResponse(401, ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleAccessDenied(AccessDeniedException ex) {
        return new ErrorResponse(403, ex.getMessage());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleMissingHeader(MissingRequestHeaderException ex) {
        return new ErrorResponse(401, "The header is missing in the request: " + ex.getHeaderName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        if ("userId".equals(ex.getName())) {
            return new ErrorResponse(400, "The userId must be a positive integer.");
        }
        return new ErrorResponse(400, "The value of " + ex.getName() + " is invalid.");
    }

    // 409 Conflict — business rule violations (RN-01 to RN-05)
    @ExceptionHandler(BusinessRuleException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleBusinessRule(BusinessRuleException ex) {
        return new ErrorResponse(409, ex.getMessage());
    }

    // 409 Conflict — pessimistic locking or concurrent transaction contention (APG-65)
    @ExceptionHandler({
            PessimisticLockingFailureException.class,
            PessimisticLockException.class,
            LockTimeoutException.class
    })
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleConcurrencyConflict(Exception ex) {
        return new ErrorResponse(409, "No se pudo completar la operación por otra petición concurrente. Intente nuevamente.");
    }

    // 404 Not Found — coupon, user or promotion does not exist
    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(ResourceNotFoundException ex) {
        return new ErrorResponse(404, ex.getMessage());
    }

    // 400 Bad Request — validation errors (missing couponId)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage())
                .findFirst()
                .orElse("Datos de entrada inválidos.");
        return new ErrorResponse(400, message);
    }

    // 400 Bad Request — malformed JSON body
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleMalformedJson(HttpMessageNotReadableException ex) {
        return new ErrorResponse(400, "El cuerpo de la petición es inválido o está vacío.");
    }

    @ExceptionHandler(PromocionDuplicadaException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handlePromocionDuplicada(PromocionDuplicadaException ex) {
        return new ErrorResponse(409, ex.getMessage());
    }
}
