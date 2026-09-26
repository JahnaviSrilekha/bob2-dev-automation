package com.payments.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request body for POST /v1/transfers.
 * Amount is a BigDecimal with max 4 decimal places (CON-003, REQ-F-005).
 * ST-001a-01
 */
public record TransferRequest(

    @NotNull(message = "senderAccountId is required")
    UUID senderAccountId,

    @NotNull(message = "receiverAccountId is required")
    UUID receiverAccountId,

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.0001", message = "amount must be greater than zero")
    @Digits(integer = 15, fraction = 4, message = "amount must have at most 4 decimal places")
    BigDecimal amount,

    @NotBlank(message = "currency is required")
    String currency
) {}
