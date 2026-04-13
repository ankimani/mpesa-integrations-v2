package com.mpesa.integration.mpesa_integrations.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "payment_reference", nullable = false, length = 128)
    private String paymentReference;

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "payment_status", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus;

    @Column(name = "merchant_request_id", length = 128)
    private String merchantRequestId;

    @Column(name = "checkout_request_id", length = 128)
    private String checkoutRequestId;

    @Column(name = "mpesa_result_code")
    private Integer mpesaResultCode;

    @Column(name = "mpesa_result_description", columnDefinition = "TEXT")
    private String mpesaResultDescription;

    @Column(name = "response_code", length = 16)
    private String responseCode;

    @Column(name = "response_description", columnDefinition = "TEXT")
    private String responseDescription;

    @Column(name = "mpesa_receipt_number", length = 64)
    private String mpesaReceiptNumber;

    @Column(name = "transaction_date")
    private OffsetDateTime transactionDate;

    @Version
    @Column(name = "version")
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}