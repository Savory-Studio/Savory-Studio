package com.apg.promociones.apg_api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI apiInfo() {
        return new OpenAPI().info(new Info()
                .title("APG Promociones API")
                .version("1.0.0")
                .description("REST API for gastronomy promotion and coupon redemption management."));
    }
}
