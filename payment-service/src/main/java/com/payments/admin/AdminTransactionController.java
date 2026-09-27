package com.payments.admin;

import com.payments.domain.Transaction;
import com.payments.domain.TransactionStatus;
import com.payments.dto.PagedAdminTransactionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.Instant;
import java.util.UUID;

/**
 * Exposes the admin transaction view at GET /v1/admin/transactions.
 * Role enforcement is handled upstream by {@link AdminRoleGuard} — callers
 * that reach this method are already verified as ADMIN.
 * REQ-F-028, REQ-F-029, REQ-F-030 — ST-011-01
 */
@RestController
@RequestMapping("/v1/admin/transactions")
public class AdminTransactionController {

    private final AdminTransactionService adminTransactionService;

    public AdminTransactionController(AdminTransactionService adminTransactionService) {
        this.adminTransactionService = adminTransactionService;
    }

    @GetMapping
    public ResponseEntity<PagedAdminTransactionResponse> getAllTransactions(
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {

        AdminTransactionFilter filter = new AdminTransactionFilter(accountId, from, to, status);
        Pageable pageable = PageRequest.of(page - 1, pageSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Transaction> result = adminTransactionService.getAllTransactions(filter, pageable);
        return ResponseEntity.ok(PagedAdminTransactionResponse.from(result));
    }
}
