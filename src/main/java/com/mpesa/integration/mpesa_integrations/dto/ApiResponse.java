package com.mpesa.integration.mpesa_integrations.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ApiResponse<T> {
    private String responseCode;
    private UUID requestId;
    private String customerMessage;
    private String technicalMessage;
    private T data;
    @Builder.Default
    private Instant timestamp = Instant.now();

    public static <T> ApiResponse<T> success(String customerMessage, String technicalMessage, T data) {
        return ApiResponse.<T>builder()
                .responseCode("200")
                .requestId(UUID.randomUUID())
                .customerMessage(customerMessage)
                .technicalMessage(technicalMessage)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> error(String responseCode, String customerMessage, String technicalMessage) {
        return ApiResponse.<T>builder()
                .responseCode(responseCode)
                .requestId(UUID.randomUUID())
                .customerMessage(customerMessage)
                .technicalMessage(technicalMessage)
                .build();
    }
}