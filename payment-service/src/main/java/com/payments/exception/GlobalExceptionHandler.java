package com.payments.exception;

import com.payments.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Maps all domain exceptions to HTTP responses with the standard error envelope.
 * Trace ID is pulled from MDC (populated by OpenTelemetry / Micrometer Tracing).
 * ST-001a-04
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String TRACE_ID_KEY = "traceId";

    private String traceId() {
        String id = MDC.get(TRACE_ID_KEY);
        return id != null ? id : "n/a";
    }

    // ── 400 Bad Request ────────────────────────────────────────────────────────

    @ExceptionHandler(InvalidAmountException.class)
    public ResponseEntity<ErrorResponse> handleInvalidAmount(InvalidAmountException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("INVALID_AMOUNT", ex.getMessage(), traceId()));
    }

    @ExceptionHandler(MissingIdempotencyKeyException.class)
    public ResponseEntity<ErrorResponse> handleMissingIdempotencyKey(MissingIdempotencyKeyException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("MISSING_IDEMPOTENCY_KEY", ex.getMessage(), traceId()));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
        if ("Idempotency-Key".equals(ex.getHeaderName())) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("MISSING_IDEMPOTENCY_KEY",
                            "Idempotency-Key header is required", traceId()));
        }
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("MISSING_HEADER", ex.getMessage(), traceId()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .findFirst()
                .orElse("Validation failed");
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("INVALID_REQUEST", message, traceId()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("INVALID_REQUEST",
                        "Invalid value for parameter '" + ex.getName() + "'", traceId()));
    }

    // ── 404 Not Found ─────────────────────────────────────────────────────────

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFound(AccountNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("ACCOUNT_NOT_FOUND", ex.getMessage(), traceId()));
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTransactionNotFound(TransactionNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("TRANSACTION_NOT_FOUND", ex.getMessage(), traceId()));
    }

    // ── 409 Conflict ──────────────────────────────────────────────────────────

    @ExceptionHandler(TransferInProgressException.class)
    public ResponseEntity<ErrorResponse> handleTransferInProgress(TransferInProgressException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("TRANSFER_IN_PROGRESS", ex.getMessage(), traceId()));
    }

    // ── 422 Unprocessable Entity ───────────────────────────────────────────────

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFunds(InsufficientFundsException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse("INSUFFICIENT_FUNDS", ex.getMessage(), traceId()));
    }

    @ExceptionHandler(SelfTransferException.class)
    public ResponseEntity<ErrorResponse> handleSelfTransfer(SelfTransferException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse("SELF_TRANSFER_NOT_ALLOWED", ex.getMessage(), traceId()));
    }

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyConflict(IdempotencyKeyConflictException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse("IDEMPOTENCY_KEY_CONFLICT", ex.getMessage(), traceId()));
    }

    // ── 503 Service Unavailable ───────────────────────────────────────────────

    @ExceptionHandler(DeadlockExhaustedException.class)
    public ResponseEntity<ErrorResponse> handleDeadlockExhausted(DeadlockExhaustedException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("TRANSFER_DEADLOCK_EXHAUSTED", ex.getMessage(), traceId()));
    }

    // ── 500 Internal Server Error ─────────────────────────────────────────────

    @ExceptionHandler(LedgerIntegrityException.class)
    public ResponseEntity<ErrorResponse> handleLedgerIntegrity(LedgerIntegrityException ex,
                                                               HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("LEDGER_INTEGRITY_ERROR", ex.getMessage(), traceId()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR",
                        "An unexpected error occurred", traceId()));
    }
}
