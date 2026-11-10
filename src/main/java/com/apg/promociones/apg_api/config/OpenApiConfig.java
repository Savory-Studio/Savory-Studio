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
                        .description("REST API for restaurant promotion publishing, active catalog queries, "
                                + "customer coupon claiming, and QR/UUID coupon redemption.\n\n"
                                + "**Authentication & Route Protection (JWT Bearer):**\n"
                                + "- **Public routes:** `POST /api/auth/register` and `POST /api/auth/login` do not require a token.\n"
                                + "- **Protected routes:** `/api/promociones/**`, `/api/coupons/**`, `/api/cupones/**`, and `/api/canjes/**` "
                                + "require `Authorization: Bearer <token>`.\n"
                                + "- Authenticate via `POST /api/auth/login` to obtain a signed HS256 JWT token, then click **Authorize** and paste the token."))
                .addSecurityItem(new SecurityRequirement()
                        .addList(BEARER_AUTH_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Signed HS256 JWT token issued by POST /api/auth/login (or TEST_AUTH_TOKEN in test environments).")));
    }
}
