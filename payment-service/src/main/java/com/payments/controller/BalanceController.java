package com.payments.controller;

import com.payments.dto.BalanceResponse;
import com.payments.service.AccountService;
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
@RestController
@RequestMapping("/v1/accounts")
public class BalanceController {

    private final AccountService accountService;

    public BalanceController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/{accountId}/balance")
    public ResponseEntity<BalanceResponse> getBalance(
            @PathVariable UUID accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant asOf) {

        if (asOf != null) {
            return ResponseEntity.ok(accountService.getBalanceAsOf(accountId, asOf));
        }
        return ResponseEntity.ok(accountService.getCurrentBalance(accountId));
    }
}
