package com.smartstay.review.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import feign.RequestInterceptor;
import lombok.extern.slf4j.Slf4j;

/**
 * Adds the X-Internal-Secret header to every outgoing Feign call.
 * This authenticates service-to-service requests that bypass the API gateway.
 * The secret value must match app.internal-secret in each target service.
 */
@Slf4j
@Configuration
public class FeignConfig {

    @Value("${app.internal-secret}")
    private String internalSecret;

    @Bean
    public RequestInterceptor internalSecretInterceptor() {
        return template -> {
            template.header("X-Internal-Secret", internalSecret);
            log.trace("X-Internal-Secret injected for Feign call to [{}]", template.url());
        };
    }
}
