package com.apg.promociones.apg_api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI apiInfo() {
        SecurityScheme testBearer = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("TestToken")
                .description("Test token (default: test-token). Replaced by JWT in the login sprint.");

        SecurityScheme userIdHeader = new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name("X-User-Id")
                .description("Simulated client identifier for the request.");

        SecurityScheme restaurantIdHeader = new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name("X-Restaurant-Id")
                .description("Optional restaurant identifier (for staff operations).");

        return new OpenAPI()
                .info(new Info()
                        .title("APG Promociones API")
                        .version("1.0.0")
                        .description("REST API for gastronomy promotion and coupon redemption management. "
                                + "Sprint 1 identity: Authorization Bearer/Basic + X-User-Id."))
                .components(new Components()
                        .addSecuritySchemes("testBearer", testBearer)
                        .addSecuritySchemes("userIdHeader", userIdHeader)
                        .addSecuritySchemes("restaurantIdHeader", restaurantIdHeader))
                .addSecurityItem(new SecurityRequirement()
                        .addList("testBearer")
                        .addList("userIdHeader"));
    }
}
