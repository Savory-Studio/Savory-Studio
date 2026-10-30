package com.apg.promociones.apg_api.exception;

import com.apg.promociones.apg_api.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
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

    // 401 Unauthorized — missing simulated identity headers or test token
    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleUnauthorized(UnauthorizedException ex) {
        return new ErrorResponse(401, ex.getMessage());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleMissingHeader(MissingRequestHeaderException ex) {
        if ("X-User-Id".equalsIgnoreCase(ex.getHeaderName())) {
            return new ErrorResponse(401, "The X-User-Id header is missing.");
        }
        return new ErrorResponse(401, "The header is missing in the request: " + ex.getHeaderName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        if ("X-User-Id".equalsIgnoreCase(ex.getName()) || "userId".equals(ex.getName())) {
            return new ErrorResponse(400, "The X-User-Id must be a positive integer.");
        }
        return new ErrorResponse(400, "The value of " + ex.getName() + " is invalid.");
    }

    // 409 Conflict — business rule violations (RN-01 to RN-05)
    @ExceptionHandler(BusinessRuleException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleBusinessRule(BusinessRuleException ex) {
        return new ErrorResponse(409, ex.getMessage());
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
}
