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

    public static final String BEARER_AUTH_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI savoryOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Savory Studio — Promotions & Coupons API")
                        .version("v1")
                        .description("Protected endpoints require a Bearer token in the Authorization header "
                                + "(configured via TEST_AUTH_TOKEN in .env until the login endpoint issues JWT tokens)."))
                .addSecurityItem(new SecurityRequirement()
                        .addList(BEARER_AUTH_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Bearer token from login (currently loaded from TEST_AUTH_TOKEN in .env)")));
    }
}
