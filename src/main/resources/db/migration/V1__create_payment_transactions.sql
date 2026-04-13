-- V1__create_payment_transactions_table.sql
CREATE TABLE IF NOT EXISTS payment_transactions (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(128) NOT NULL,
    payment_reference VARCHAR(128) NOT NULL,
    phone_number VARCHAR(20) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    payment_status VARCHAR(32) NOT NULL,
    merchant_request_id VARCHAR(128),
    checkout_request_id VARCHAR(128),
    mpesa_result_code INTEGER,
    mpesa_result_description TEXT,
    response_code VARCHAR(16),
    response_description TEXT,
    mpesa_receipt_number VARCHAR(64),
    transaction_date TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_payment_checkout ON payment_transactions(checkout_request_id);
CREATE INDEX idx_payment_merchant ON payment_transactions(merchant_request_id);
CREATE INDEX idx_payment_status ON payment_transactions(payment_status);
CREATE INDEX idx_payment_receipt ON payment_transactions(mpesa_receipt_number);
CREATE INDEX idx_payment_created_at_desc ON payment_transactions(created_at DESC);
CREATE INDEX idx_payment_status_created_at_desc ON payment_transactions(payment_status, created_at DESC);