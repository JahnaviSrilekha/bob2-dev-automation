package com.payments.controller;

import com.payments.dto.BalanceResponse;
import com.payments.dto.ErrorResponse;
import com.payments.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.Instant;
import java.util.UUID;

/**
 * GET /v1/accounts/{accountId}/balance
 * Optional ?asOf= query param for point-in-time balance (REQ-F-018, REQ-F-019).
 * ST-003-01
 */
@Tag(name = "Accounts", description = "Balance inquiry and transaction history")
@RestController
@RequestMapping("/v1/accounts")
public class BalanceController {

    private final AccountService accountService;

    public BalanceController(AccountService accountService) {
        this.accountService = accountService;
    }

    @Operation(
        summary     = "Get account balance",
        description = "Returns the current authoritative balance. Append ?asOf=<ISO-8601 timestamp> for a point-in-time balance."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Balance returned",
            content = @Content(schema = @Schema(implementation = BalanceResponse.class))),
        @ApiResponse(responseCode = "404", description = "Account not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{accountId}/balance")
    public ResponseEntity<BalanceResponse> getBalance(
            @Parameter(description = "Account UUID", required = true)
            @PathVariable UUID accountId,
            @Parameter(description = "ISO-8601 UTC timestamp for point-in-time balance (optional)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant asOf) {

        if (asOf != null) {
            return ResponseEntity.ok(accountService.getBalanceAsOf(accountId, asOf));
        }
        return ResponseEntity.ok(accountService.getCurrentBalance(accountId));
    }
}
