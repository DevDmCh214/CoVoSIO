package com.covosio.exception;

/**
 * Thrown when a business rule is violated (e.g. R01–R11).
 * Maps to HTTP 400 in GlobalExceptionHandler.
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
