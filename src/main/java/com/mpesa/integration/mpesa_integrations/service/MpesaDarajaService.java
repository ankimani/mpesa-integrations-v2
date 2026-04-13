package com.mpesa.integration.mpesa_integrations.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpesa.integration.mpesa_integrations.config.MpesaDarajaConfig;
import com.mpesa.integration.mpesa_integrations.dto.PaymentRequest;
import com.mpesa.integration.mpesa_integrations.exception.MpesaException;
import com.mpesa.integration.mpesa_integrations.request.MpesaStkPushRequest;
import com.mpesa.integration.mpesa_integrations.response.MpesaStkPushResponse;
import com.mpesa.integration.mpesa_integrations.util.StructuredPaymentLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class MpesaDarajaService {

    private final RestClient restClient;
    private final MpesaDarajaConfig config;
    private final StructuredPaymentLogger structuredLogger;
    private final ObjectMapper objectMapper;

    @Value("${spring.application.name:payment-service}")
    private String serviceName;

    private static final int LOG_BODY_MAX = 512;

    public MpesaStkPushResponse initiateStkPush(PaymentRequest request, String paymentReference, String traceId) {
        long startTime = System.currentTimeMillis();

        try {
            // Get access token
            String accessToken = getAccessToken();

            // Build STK push request
            String timestamp = getTimestamp();
            String password = generatePassword(timestamp);

            MpesaStkPushRequest stkRequest = MpesaStkPushRequest.builder()
                    .businessShortCode(config.getShortcode())
                    .password(password)
                    .timestamp(timestamp)
                    .transactionType(config.getTransactionType())
                    .amount(request.getAmount().toString())
                    .partyA(request.getPhoneNumber())
                    .partyB(config.getShortcode())
                    .phoneNumber(request.getPhoneNumber())
                    .callBackURL(config.getCallbackUrl())
                    .accountReference(paymentReference.substring(0, Math.min(12, paymentReference.length())))
                    .transactionDesc("Payment")
                    .build();

            // Make STK push request
            ResponseEntity<MpesaStkPushResponse> response = restClient.post()
                    .uri("/mpesa/stkpush/v1/processrequest")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(stkRequest)
                    .retrieve()
                    .toEntity(MpesaStkPushResponse.class);

            long duration = System.currentTimeMillis() - startTime;
            structuredLogger.logPaymentEvent(serviceName, "STK_PUSH", traceId, "/mpesa/stkpush",
                    duration, 200, "STK Push successful", null, stkRequest, response.getBody());

            return response.getBody();

        } catch (RestClientResponseException e) {
            long duration = System.currentTimeMillis() - startTime;
            String responseBody = e.getResponseBodyAsString();
            structuredLogger.logPaymentEvent(serviceName, "STK_PUSH", traceId, "/mpesa/stkpush",
                    duration, e.getStatusCode().value(), "STK Push failed", e.getMessage(), request, null);
            throw mapErrorResponse(responseBody);

        } catch (MpesaException e) {
            long duration = System.currentTimeMillis() - startTime;
            structuredLogger.logPaymentEvent(serviceName, "STK_PUSH", traceId, "/mpesa/stkpush",
                    duration, 400, "STK Push failed", e.getMessage(), request, null);
            throw e;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            structuredLogger.logPaymentEvent(serviceName, "STK_PUSH", traceId, "/mpesa/stkpush",
                    duration, 500, "STK Push failed", e.getMessage(), request, null);
            throw new MpesaException("500.003.1001", "Unexpected error: " + e.getMessage());
        }
    }

    private String getAccessToken() {
        long oauthStart = System.currentTimeMillis();
        log.info("Daraja OAuth: requesting access token | baseUrl={}", config.getBaseUrl());

        try {
            String credentials = config.getConsumerKey() + ":" + config.getConsumerSecret();
            String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());

            // Daraja OAuth: GET + Basic auth (not POST). POST with a body often returns 200 with empty "{}".
            ResponseEntity<String> response = restClient.get()
                    .uri("/oauth/v1/generate?grant_type=client_credentials")
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + encodedCredentials)
                    .retrieve()
                    .toEntity(String.class);

            long durationMs = System.currentTimeMillis() - oauthStart;
            String raw = response.getBody();
            if (raw == null || raw.isBlank()) {
                log.warn("Daraja OAuth: empty response body | httpStatus={} | durationMs={}",
                        response.getStatusCode(), durationMs);
                throw new MpesaException("404.001.03", "Empty body from OAuth token endpoint (status=" + response.getStatusCode() + ")");
            }

            JsonNode json = objectMapper.readTree(raw);

            if (json.has("access_token")) {
                String token = json.get("access_token").asText();
                String expires = json.has("expires_in") ? json.get("expires_in").asText() : "n/a";
                log.info("Daraja OAuth: access token obtained | durationMs={} | expiresIn={} | tokenChars={}",
                        durationMs, expires, token.length());
                return token;
            }

            // OAuth 2.0 error body (RFC 6749) — often returned instead of Daraja errorCode/errorMessage
            if (json.has("error")) {
                String oauthErr = json.get("error").asText();
                String desc = json.has("error_description") ? json.get("error_description").asText() : oauthErr;
                log.warn("Daraja OAuth: token endpoint returned error JSON | durationMs={} | error={} | description={} | bodySnippet={}",
                        durationMs, oauthErr, desc, truncateForLog(raw));
                throw new MpesaException("404.001.03", desc);
            }

            log.warn("Daraja OAuth: response JSON has no access_token | httpStatus={} | durationMs={} | bodySnippet={}",
                    response.getStatusCode(), durationMs, truncateForLog(raw));
            throw new MpesaException("404.001.03", "No access token in response");

        } catch (RestClientResponseException e) {
            long durationMs = System.currentTimeMillis() - oauthStart;
            String responseBody = e.getResponseBodyAsString();
            log.warn("Daraja OAuth: HTTP error from token endpoint | status={} | durationMs={} | bodySnippet={}",
                    e.getStatusCode(), durationMs, truncateForLog(responseBody));
            throw mapErrorResponse(responseBody);

        } catch (MpesaException e) {
            long durationMs = System.currentTimeMillis() - oauthStart;
            log.warn("Daraja OAuth: failed | durationMs={} | code={} | message={}",
                    durationMs, e.getErrorCode(), e.getErrorMessage());
            throw e;

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - oauthStart;
            log.warn("Daraja OAuth: unexpected failure | durationMs={} | errorClass={} | message={}",
                    durationMs, e.getClass().getSimpleName(), e.getMessage());
            throw new MpesaException("500.003.1001", "Failed to get access token: " + e.getMessage());
        }
    }

    private static String truncateForLog(String s) {
        if (s == null) {
            return "null";
        }
        String t = s.replace("\r", " ").replace("\n", " ").trim();
        return t.length() <= LOG_BODY_MAX ? t : t.substring(0, LOG_BODY_MAX) + "...(truncated)";
    }

    /** Parses Daraja error JSON and returns the exception to throw (never returns normally). */
    private MpesaException mapErrorResponse(String responseBody) {
        try {
            if (responseBody == null || responseBody.isEmpty()) {
                return new MpesaException("500.003.1001", "Empty error response from Mpesa");
            }

            JsonNode errorJson = objectMapper.readTree(responseBody);

            String errorCode = errorJson.has("errorCode") ? errorJson.get("errorCode").asText() : "500.003.1001";
            String errorMessage = errorJson.has("errorMessage") ? errorJson.get("errorMessage").asText() : "Unknown error";

            if (errorMessage.contains("Invalid BusinessShortCode")) {
                return new MpesaException("400.002.02", errorMessage);
            } else if (errorMessage.contains("Invalid Access Token")) {
                return new MpesaException("404.001.03", errorMessage);
            } else if (errorMessage.contains("Wrong credentials") || errorMessage.contains("Invalid Password")) {
                return new MpesaException("500.001.1001", errorMessage);
            } else if (errorMessage.contains("Merchant does not exist")) {
                return new MpesaException("500.001.1001", errorMessage);
            } else if (errorMessage.contains("already in process")) {
                return new MpesaException("500.001.1001", errorMessage);
            } else if (errorMessage.contains("System is busy") || errorMessage.contains("Spike Arrest")) {
                return new MpesaException("500.003.02", errorMessage);
            } else if (errorMessage.contains("Quota Violation")) {
                return new MpesaException("500.003.03", errorMessage);
            }
            return new MpesaException(errorCode, errorMessage);

        } catch (Exception e) {
            return new MpesaException("500.003.1001", "Error processing response: " + responseBody);
        }
    }

    private String generatePassword(String timestamp) {
        String passwordStr = config.getShortcode() + config.getPasskey() + timestamp;
        return Base64.getEncoder().encodeToString(passwordStr.getBytes());
    }

    private String getTimestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }
}