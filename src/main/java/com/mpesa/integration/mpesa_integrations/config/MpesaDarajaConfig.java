package com.mpesa.integration.mpesa_integrations.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "mpesa.daraja")
public class MpesaDarajaConfig {
    private String consumerKey;
    private String consumerSecret;
    private String passkey;
    private String shortcode;
    private String callbackUrl;
    private String callbackTokenSecret;
    private boolean callbackSecurityEnabled;
    private String baseUrl;
    private String transactionType;

    public String effectiveCallbackSecretToken() {
        return callbackTokenSecret;
    }
}