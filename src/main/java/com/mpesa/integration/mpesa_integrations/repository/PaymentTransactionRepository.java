package com.mpesa.integration.mpesa_integrations.repository;

import com.mpesa.integration.mpesa_integrations.entity.PaymentStatus;
import com.mpesa.integration.mpesa_integrations.entity.PaymentTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {
    Optional<PaymentTransaction> findByIdempotencyKey(String idempotencyKey);
    Optional<PaymentTransaction> findByCheckoutRequestId(String checkoutRequestId);
    Page<PaymentTransaction> findByPaymentStatus(PaymentStatus status, Pageable pageable);
    Page<PaymentTransaction> findAll(Pageable pageable);
}
