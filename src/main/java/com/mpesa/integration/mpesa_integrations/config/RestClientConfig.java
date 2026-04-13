package com.mpesa.integration.mpesa_integrations.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    private final MpesaDarajaConfig mpesaDarajaConfig;

    public RestClientConfig(MpesaDarajaConfig mpesaDarajaConfig) {
        this.mpesaDarajaConfig = mpesaDarajaConfig;
    }

    @Bean
    public RestClient mpesaRestClient() {
        return RestClient.builder()
                .baseUrl(mpesaDarajaConfig.getBaseUrl())
                .build();
    }
}