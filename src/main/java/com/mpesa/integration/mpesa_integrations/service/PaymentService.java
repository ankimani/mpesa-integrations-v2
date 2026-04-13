package com.mpesa.integration.mpesa_integrations.service;

import com.mpesa.integration.mpesa_integrations.dto.MpesaCallback;
import com.mpesa.integration.mpesa_integrations.dto.PaymentRequest;
import com.mpesa.integration.mpesa_integrations.entity.PaymentStatus;
import com.mpesa.integration.mpesa_integrations.entity.PaymentTransaction;
import com.mpesa.integration.mpesa_integrations.exception.MpesaException;
import com.mpesa.integration.mpesa_integrations.repository.PaymentTransactionRepository;
import com.mpesa.integration.mpesa_integrations.response.MpesaStkPushResponse;
import com.mpesa.integration.mpesa_integrations.util.StructuredPaymentLogger;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentTransactionRepository transactionRepository;
    private final MpesaDarajaService mpesaDarajaService;
    private final StructuredPaymentLogger structuredLogger;

    @Value("${spring.application.name:payment-service}")
    private String serviceName;

    @Transactional
    public PaymentTransaction initiatePayment(String idempotencyKey, PaymentRequest request, String traceId) {
        long startTime = System.currentTimeMillis();

        // Check for duplicate
        Optional<PaymentTransaction> existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            throw new MpesaException("4003", "Duplicate request");
        }

        // Create transaction (id + version are assigned by JPA on persist — do not set manually)
        PaymentTransaction transaction = PaymentTransaction.builder()
                .idempotencyKey(idempotencyKey)
                .paymentReference(generatePaymentReference())
                .phoneNumber(request.getPhoneNumber())
                .amount(request.getAmount())
                .paymentStatus(PaymentStatus.PENDING)
                .build();

        transaction = transactionRepository.save(transaction);

        try {
            // Call Mpesa
            MpesaStkPushResponse response = mpesaDarajaService.initiateStkPush(request, transaction.getPaymentReference(), traceId);

            // Update transaction
            transaction.setMerchantRequestId(response.getMerchantRequestId());
            transaction.setCheckoutRequestId(response.getCheckoutRequestId());
            transaction.setResponseCode(response.getResponseCode());
            transaction.setResponseDescription(response.getResponseDescription());
            transaction.setPaymentStatus("0".equals(response.getResponseCode()) ? PaymentStatus.PROCESSING : PaymentStatus.FAILED);

            structuredLogger.logPaymentEvent(serviceName, "INITIATE", traceId, "/payments/initiate",
                    System.currentTimeMillis() - startTime, 200, "Payment initiated", null,
                    initiateRequestLog(idempotencyKey, request), transaction);

            return transactionRepository.save(transaction);

        } catch (MpesaException e) {
            transaction.setPaymentStatus(PaymentStatus.FAILED);
            transaction.setResponseDescription(e.getMessage());
            transactionRepository.save(transaction);

            structuredLogger.logPaymentEvent(serviceName, "INITIATE", traceId, "/payments/initiate",
                    System.currentTimeMillis() - startTime, 400, "Payment failed", e.getMessage(),
                    initiateRequestLog(idempotencyKey, request), null);
            throw e;
        }
    }

    @Transactional
    public void processCallback(MpesaCallback callback, String traceId) {
        MpesaCallback.StkCallback stkCallback = callback.getBody().getStkCallback();

        Optional<PaymentTransaction> transactionOpt = transactionRepository
                .findByCheckoutRequestId(stkCallback.getCheckoutRequestId());

        if (transactionOpt.isEmpty()) {
            throw new MpesaException("4004", "Transaction not found");
        }

        PaymentTransaction transaction = transactionOpt.get();
        transaction.setMpesaResultCode(stkCallback.getResultCode());
        transaction.setMpesaResultDescription(stkCallback.getResultDesc());

        // Handle result codes
        if (stkCallback.getResultCode() == 0) {
            // Success - extract metadata
            if (stkCallback.getCallbackMetadata() != null) {
                stkCallback.getCallbackMetadata().getItems().forEach(item -> {
                    if ("MpesaReceiptNumber".equals(item.getName())) {
                        transaction.setMpesaReceiptNumber(item.getValue().toString());
                    } else if ("TransactionDate".equals(item.getName())) {
                        OffsetDateTime txDate = parseMpesaTransactionDate(item.getValue());
                        if (txDate != null) {
                            transaction.setTransactionDate(txDate);
                        }
                    }
                });
            }
            transaction.setPaymentStatus(PaymentStatus.COMPLETED);

        } else if (stkCallback.getResultCode() == 1037) {
            transaction.setPaymentStatus(PaymentStatus.TIMEOUT);

        } else if (stkCallback.getResultCode() == 1032 || stkCallback.getResultCode() == 1003) {
            transaction.setPaymentStatus(PaymentStatus.CANCELLED);

        } else {
            transaction.setPaymentStatus(PaymentStatus.FAILED);
        }

        transactionRepository.save(transaction);

        structuredLogger.logPaymentEvent(serviceName, "CALLBACK", traceId, "/payments/callback",
                0, 200, "Callback processed", null, callback, transaction);
    }

    /** @param pageOneBased first page is 1 (not 0) */
    public Page<PaymentTransaction> getTransactions(PaymentStatus status, int pageOneBased, int size) {
        int pageIndex = pageOneBased - 1;
        Pageable pageable = PageRequest.of(pageIndex, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return status != null ?
                transactionRepository.findByPaymentStatus(status, pageable) :
                transactionRepository.findAll(pageable);
    }

    private static Map<String, Object> initiateRequestLog(String idempotencyKey, PaymentRequest request) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("idempotencyKey", idempotencyKey);
        m.put("phoneNumber", request.getPhoneNumber());
        m.put("amount", request.getAmount());
        return m;
    }

    private String generatePaymentReference() {
        return "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /** Parses STK CallbackMetadata TransactionDate (numeric or string, yyyyMMddHHmmss, Africa/Nairobi). */
    private OffsetDateTime parseMpesaTransactionDate(Object value) {
        if (value == null) {
            return null;
        }
        String digits;
        if (value instanceof BigDecimal bd) {
            digits = String.format("%014d", bd.longValue());
        } else if (value instanceof Double || value instanceof Float) {
            digits = String.format("%014d", Math.round(((Number) value).doubleValue()));
        } else if (value instanceof Number n) {
            digits = String.format("%014d", n.longValue());
        } else {
            digits = value.toString().replaceAll("\\D", "");
        }
        if (digits.length() < 14) {
            log.warn("TransactionDate value too short after normalizing: raw={}", value);
            return null;
        }
        digits = digits.substring(0, 14);
        try {
            LocalDateTime ldt = LocalDateTime.parse(digits, DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            return ldt.atZone(ZoneId.of("Africa/Nairobi")).toOffsetDateTime();
        } catch (Exception e) {
            log.warn("Failed to parse TransactionDate: {}", digits, e);
            return null;
        }
    }
}