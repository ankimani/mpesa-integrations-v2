package com.mpesa.integration.mpesa_integrations.util;

public final class AppConstants {
    private AppConstants() {
    }

    public static final String SUCCESS_CODE = "0";
    public static final String CANCELLED_CODE = "1032";
    public static final String TIMEOUT_CODE = "1037";
    public static final String INSUFFICIENT_FUNDS_CODE = "1";
    public static final String INVALID_REQUEST_CODE = "2001";
    public static final String CODE_404 = "404";
    public static final String CODE_400 = "400";
    public static final String CODE_500 = "500";
    public static final String CODE_200 = "200";


    public static final String MSG_CANCELLED =
            "You cancelled the transaction.";

    public static final String MSG_TIMEOUT =
            "Transaction timed out. Please try again.";

    public static final String MSG_INSUFFICIENT_FUNDS =
            "You do not have enough balance.";

    public static final String MSG_INVALID_REQUEST =
            "Invalid phone number or initiator.";

    public static final String MSG_DEFAULT =
            "We were unable to complete your payment.";

    public static final String FAILED_VALIDATION_MSG = "Validation failed";

    public static final String SERVER_ERROR_MSG = "We are unable to process your request right now. Please try again in later.";
    public static final String UNEXPECTED_ERROR_MSG = "An unexpected error occurred";
    public static final String PAYMENT_PROCESSING_FAILED = "Payment processing failed";
    public static final String INVALID_REQUEST_PARAMETER = "Invalid request parameter";
    public static final String MISSING_REQUIRED_HEADER = "Required header is missing: ";
}