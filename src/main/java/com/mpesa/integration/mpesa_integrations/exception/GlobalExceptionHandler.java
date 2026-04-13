package com.mpesa.integration.mpesa_integrations.exception;

import com.mpesa.integration.mpesa_integrations.dto.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static com.mpesa.integration.mpesa_integrations.util.AppConstants.*;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MpesaException.class)
    public ResponseEntity<ApiResponse<Void>> handleMpesaException(MpesaException e) {
        log.error("Mpesa error: code={}, message={}", e.getErrorCode(), e.getErrorMessage());

        String customerMessage = getCustomerMessage(e.getErrorCode());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getErrorCode(), customerMessage, e.getErrorMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String errorMessage = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(CODE_400, errorMessage,FAILED_VALIDATION_MSG));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException e) {
        String errorMessage = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .findFirst()
                .orElse(INVALID_REQUEST_PARAMETER);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(CODE_400, errorMessage,FAILED_VALIDATION_MSG));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingRequestHeader(MissingRequestHeaderException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(CODE_400,
                        MISSING_REQUIRED_HEADER + e.getHeaderName(),FAILED_VALIDATION_MSG));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception e) {
        log.error(UNEXPECTED_ERROR_MSG, e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(CODE_500, SERVER_ERROR_MSG, UNEXPECTED_ERROR_MSG));
    }

    private String getCustomerMessage(String errorCode) {
        if (errorCode == null) {
            return PAYMENT_PROCESSING_FAILED;
        }
        return switch (errorCode) {
            case CANCELLED_CODE -> MSG_CANCELLED;
            case TIMEOUT_CODE -> MSG_TIMEOUT;
            case INSUFFICIENT_FUNDS_CODE -> MSG_INSUFFICIENT_FUNDS;
            case INVALID_REQUEST_CODE -> MSG_INVALID_REQUEST;
            default -> MSG_DEFAULT;
        };
        }

}