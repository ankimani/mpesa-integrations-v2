package com.mpesa.integration.mpesa_integrations.controller;

import com.mpesa.integration.mpesa_integrations.config.MpesaDarajaConfig;
import com.mpesa.integration.mpesa_integrations.dto.ApiResponse;
import com.mpesa.integration.mpesa_integrations.dto.MpesaCallback;
import com.mpesa.integration.mpesa_integrations.dto.PaymentRequest;
import com.mpesa.integration.mpesa_integrations.entity.PaymentStatus;
import com.mpesa.integration.mpesa_integrations.entity.PaymentTransaction;
import com.mpesa.integration.mpesa_integrations.exception.MpesaException;
import com.mpesa.integration.mpesa_integrations.response.PaginationResponse;
import com.mpesa.integration.mpesa_integrations.service.PaymentService;
import com.mpesa.integration.mpesa_integrations.util.StructuredPaymentLogger;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final MpesaDarajaConfig config;
    private final StructuredPaymentLogger structuredPaymentLogger;

    @Value("${spring.application.name:mpesa-integrations}")
    private String applicationName;

    @PostMapping("/initiate")
    public ResponseEntity<ApiResponse<PaymentTransaction>> initiatePayment(
            @RequestHeader(value = "Idempotency-Key", required = true)
            @NotBlank(message = "Idempotency-Key header must not be empty")
            @Size(max = 128, message = "Idempotency-Key must be at most 128 characters")
            String idempotencyKey,
            @Valid @RequestBody PaymentRequest request) {
        String traceId = UUID.randomUUID().toString();
        try {
            PaymentTransaction transaction = paymentService.initiatePayment(idempotencyKey, request, traceId);
            return ResponseEntity.ok(ApiResponse.success(
                    "Payment initiated successfully",
                    "STK Push sent to customer",
                    transaction
            ));
        } catch (MpesaException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getErrorCode(), "Payment failed", e.getMessage()));
        }
    }

    @PostMapping("/callback/{token}")
    public ResponseEntity<Void> handleCallback(@PathVariable String token, @RequestBody MpesaCallback callback) {
        // Security check
        if (config.isCallbackSecurityEnabled() &&
                !token.equals(config.effectiveCallbackSecretToken())) {
            log.warn("Invalid callback token");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String traceId = UUID.randomUUID().toString();
        try {
            paymentService.processCallback(callback, traceId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Callback failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<PaginationResponse<PaymentTransaction>>> getTransactions(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        String traceId = UUID.randomUUID().toString();
        long start = System.currentTimeMillis();
        Page<PaymentTransaction> transactions = paymentService.getTransactions(status, page, size);

        Map<String, Object> requestLog = new LinkedHashMap<>();
        if (status != null) {
            requestLog.put("status", status.name());
        }
        requestLog.put("page", page);
        requestLog.put("size", size);

        structuredPaymentLogger.logPaymentEventPaginated(
                applicationName,
                "LIST_TRANSACTIONS",
                traceId,
                "/api/v1/payments/transactions",
                System.currentTimeMillis() - start,
                200,
                "Transactions retrieved",
                null,
                requestLog,
                transactions
        );

        PaginationResponse<PaymentTransaction> response = PaginationResponse.<PaymentTransaction>builder()
                .content(transactions.getContent())
                .pageNumber(transactions.getNumber() + 1)
                .pageSize(transactions.getSize())
                .totalElements(transactions.getTotalElements())
                .totalPages(transactions.getTotalPages())
                .last(transactions.isLast())
                .build();

        return ResponseEntity.ok(ApiResponse.success(
                "Transactions retrieved",
                "Paginated results",
                response
        ));
    }
}
