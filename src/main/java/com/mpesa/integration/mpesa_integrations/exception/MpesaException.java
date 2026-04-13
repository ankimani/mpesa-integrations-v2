package com.mpesa.integration.mpesa_integrations.exception;

import lombok.Getter;

@Getter
public class MpesaException extends RuntimeException {
    private final String errorCode;
    private final String errorMessage;

    public MpesaException(String errorCode, String errorMessage) {
        super(errorMessage);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public MpesaException(String errorCode, String errorMessage, Throwable cause) {
        super(errorMessage, cause);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }
}
