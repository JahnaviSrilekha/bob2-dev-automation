package com.payments.dto;

/**
 * Standard error envelope for all error responses (CON-005, §8.3).
 */
public record ErrorResponse(
    String errorCode,
    String message,
    String traceId
) {}
