package com.mpesa.integration.mpesa_integrations.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class StructuredPaymentLogger {
    private static final Logger log = LoggerFactory.getLogger(StructuredPaymentLogger.class);
    private final ObjectMapper objectMapper;

    public void logPaymentEvent(
            String serviceName,
            String processName,
            String traceId,
            String endpointPath,
            long durationMillis,
            int httpStatusCode,
            String outcomeMessage,
            String errorDetail,
            Object requestPayload,
            Object responsePayload
    ) {
        String requestJson = sanitizeJson(serializePayload(requestPayload));
        String responseJson = sanitizeJson(serializePayload(responsePayload));
        emitLine(serviceName, processName, traceId, endpointPath, durationMillis, httpStatusCode,
                outcomeMessage, errorDetail, requestJson, responseJson);
    }

    /**
     * Same pipe-delimited format as {@link #logPaymentEvent}, but {@code response} is pagination metadata only
     * (no row/entity payloads), suitable for list endpoints with large result sets.
     */
    public void logPaymentEventPaginated(
            String serviceName,
            String processName,
            String traceId,
            String endpointPath,
            long durationMillis,
            int httpStatusCode,
            String outcomeMessage,
            String errorDetail,
            Object requestPayload,
            Page<?> page
    ) {
        String requestJson = sanitizeJson(serializePayload(requestPayload));
        String responseJson = sanitizeJson(summarizePage(page));
        emitLine(serviceName, processName, traceId, endpointPath, durationMillis, httpStatusCode,
                outcomeMessage, errorDetail, requestJson, responseJson);
    }

    private void emitLine(
            String serviceName,
            String processName,
            String traceId,
            String endpointPath,
            long durationMillis,
            int httpStatusCode,
            String outcomeMessage,
            String errorDetail,
            String requestJson,
            String responseJson
    ) {
        String errorDetailEscaped = (errorDetail == null || errorDetail.isBlank())
                ? "NONE"
                : errorDetail.replace("|", "\\|");
        String outcomeEscaped = outcomeMessage == null ? "" : outcomeMessage.replace("|", "\\|");

        String line = String.format(
                "%s | service=%s | process=%s | traceId=%s | endpoint=%s | duration=%dms | responseCode=%d | responseMessage=%s | errorMessage=%s | request=%s | response=%s",
                Instant.now(),
                serviceName,
                processName,
                traceId,
                endpointPath,
                durationMillis,
                httpStatusCode,
                outcomeEscaped,
                errorDetailEscaped,
                requestJson,
                responseJson
        );

        if (httpStatusCode >= 500) {
            log.error(line);
        } else if (httpStatusCode >= 400) {
            log.warn(line);
        } else {
            log.info(line);
        }
    }

    private String summarizePage(Page<?> page) {
        if (page == null) {
            return "null";
        }
        try {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("pageNumber", page.getNumber() + 1);
            summary.put("pageSize", page.getSize());
            summary.put("totalElements", page.getTotalElements());
            summary.put("totalPages", page.getTotalPages());
            summary.put("returned", page.getNumberOfElements());
            summary.put("last", page.isLast());
            return objectMapper.writeValueAsString(summary);
        } catch (Exception e) {
            return "{\"error\":\"summarizePage\"}";
        }
    }

    private String serializePayload(Object payload) {
        if (payload == null) return "null";
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return payload.toString();
        }
    }

    private String sanitizeJson(String json) {
        if (json == null) return "null";
        return json.replace("\n", " ").replace("\r", " ");
    }
}