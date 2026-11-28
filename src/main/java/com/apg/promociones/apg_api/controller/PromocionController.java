package com.apg.promociones.apg_api.controller;

import com.apg.promociones.apg_api.config.OpenApiConfig;
import com.apg.promociones.apg_api.dto.ActivePromotionResponse;
import com.apg.promociones.apg_api.dto.ErrorResponse;
import com.apg.promociones.apg_api.dto.PromocionRequest;
import com.apg.promociones.apg_api.dto.PromocionResponse;
import com.apg.promociones.apg_api.service.PromocionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.apg.promociones.apg_api.dto.PromotionMetricsResponse;
import com.apg.promociones.apg_api.service.PromotionMetricsService;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@RestController
@RequestMapping("/api/promociones")
@RequiredArgsConstructor
@Tag(name = "Promociones", description = "Promotion publishing and active catalog endpoints (requires JWT Bearer token)")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
public class PromocionController {

        private final PromocionService promocionService;

        @GetMapping("/activas")
        @ResponseStatus(HttpStatus.OK)
        @Operation(summary = "Get active promotions", description = "Retrieves the catalog of currently active promotions within their validity dates and available usage limits.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Active promotions retrieved successfully", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ActivePromotionResponse.class)))),
                        @ApiResponse(responseCode = "401", description = "Missing, invalid, or expired Bearer token", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
        })
        public List<ActivePromotionResponse> getActivePromotions() {
                return promocionService.getActivePromotions();
        }

        @PostMapping
        @ResponseStatus(HttpStatus.CREATED)
        @Operation(summary = "Publish a new promotion", description = "Creates a new promotion (discount) for a restaurant, validating format, conditions and limits. Allowed for the restaurant owner or linked authorized staff (REST-05).")
        @ApiResponses({
                        @ApiResponse(responseCode = "201", description = "Promotion created successfully", content = @Content(schema = @Schema(implementation = PromocionResponse.class))),
                        @ApiResponse(responseCode = "400", description = "Validation error", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                        @ApiResponse(responseCode = "401", description = "Missing, invalid, or expired Bearer token", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                        @ApiResponse(responseCode = "403", description = "Authenticated user is neither the restaurant owner nor authorized staff for this restaurant", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                        @ApiResponse(responseCode = "404", description = "Restaurant not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
        })
        public PromocionResponse create(@Valid @RequestBody PromocionRequest request) {
                return promocionService.create(request);
        }

        private final PromotionMetricsService promotionMetricsService;

        @GetMapping("/{discountId}/metrics")
        @ResponseStatus(HttpStatus.OK)
        @Operation(
                summary = "Get aggregate metrics for a promotion",
                description = "Retrieves aggregate counters (views, claimed coupons, and redeemed coupons) for a specific promotion. "
                        + "Accessible exclusively by the owner of the restaurant that owns the promotion. Does not expose customer identities."
        )
        @ApiResponses({
                @ApiResponse(responseCode = "200", description = "Promotion metrics retrieved successfully",
                        content = @Content(schema = @Schema(implementation = PromotionMetricsResponse.class))),
                @ApiResponse(responseCode = "400", description = "Invalid discountId format",
                        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                @ApiResponse(responseCode = "401", description = "Missing, invalid, or expired Bearer token",
                        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                @ApiResponse(responseCode = "403", description = "Authenticated user is not the owner of the promotion's restaurant",
                        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                @ApiResponse(responseCode = "404", description = "Promotion not found",
                        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
        })
        public PromotionMetricsResponse getPromotionMetrics(
                @Parameter(description = "ID of the promotion (discount)", example = "1", required = true)
                @PathVariable Integer discountId) {
                return promotionMetricsService.getMetricsByPromotionId(discountId);
        }
}
