package com.example.price_service.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI priceServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Last Value Price Service API")
                        .description("Service for publishing and retrieving the last price of financial instruments.")
                        .version("2.0"));
    }
}