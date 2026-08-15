package com.coupleai.coupleai.beinema.Config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class MetisConfig {

    @Bean
    public RestClient metisRestClient(
            @Value("${metis.base-url}") String baseUrl,
            @Value("${metis.api-key}") String apiKey
    ) {

        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(
                        "Authorization",
                        "Bearer " + apiKey
                )
                .defaultHeader(
                        "Content-Type",
                        "application/json"
                )
                .build();
    }
}