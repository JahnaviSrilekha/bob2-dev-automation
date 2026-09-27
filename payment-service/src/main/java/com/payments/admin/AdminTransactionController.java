package com.payments.admin;

import com.payments.domain.Transaction;
import com.payments.domain.TransactionStatus;
import com.payments.dto.ErrorResponse;
import com.payments.dto.PagedAdminTransactionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Admin", description = "Admin-only endpoints — requires X-User-Role: ADMIN header")
@SecurityRequirement(name = "AdminRoleHeader")
@RestController
@RequestMapping("/v1/admin/transactions")
public class AdminTransactionController {

    private final AdminTransactionService adminTransactionService;

    public AdminTransactionController(AdminTransactionService adminTransactionService) {
        this.adminTransactionService = adminTransactionService;
    }

    @Operation(
        summary     = "List all transactions (admin)",
        description = "Returns a paginated list of all transactions across all accounts. Requires X-User-Role: ADMIN header. All query parameters are optional — omitting them returns all transactions."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Paginated transaction list returned",
            content = @Content(schema = @Schema(implementation = PagedAdminTransactionResponse.class))),
        @ApiResponse(responseCode = "403", description = "Caller is not an admin",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<PagedAdminTransactionResponse> getAllTransactions(
            @Parameter(description = "Filter by account UUID (matches sender OR receiver)") @RequestParam(required = false) UUID accountId,
            @Parameter(description = "Filter from this timestamp (ISO-8601 UTC, inclusive)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(description = "Filter to this timestamp (ISO-8601 UTC, inclusive)")  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @Parameter(description = "Filter by status: PENDING | COMPLETED | FAILED | REVERSED") @RequestParam(required = false) TransactionStatus status,
            @Parameter(description = "1-based page number (default: 1)") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Page size (default: 20)") @RequestParam(defaultValue = "20") int pageSize) {

        AdminTransactionFilter filter = new AdminTransactionFilter(accountId, from, to, status);
        Pageable pageable = PageRequest.of(page - 1, pageSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Transaction> result = adminTransactionService.getAllTransactions(filter, pageable);
        return ResponseEntity.ok(PagedAdminTransactionResponse.from(result));
    }
}
