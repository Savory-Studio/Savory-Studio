package com.apg.promociones.apg_api.controller;

import com.apg.promociones.apg_api.dto.AssociatePromotionRequest;
import com.apg.promociones.apg_api.dto.AssociatePromotionResponse;
import com.apg.promociones.apg_api.dto.ErrorResponse;
import com.apg.promociones.apg_api.service.AssociatePromotionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
@Tag(name = "Coupons", description = "Associate current promotions with the customer profile")
public class CouponController {

    private final AssociatePromotionService associatePromotionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Associate a promotion with the customer profile",
            description = "Validates that the promotion exists and is currently valid, then creates an UNUSED coupon on the user profile."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Promotion associated with the profile"),
            @ApiResponse(responseCode = "400", description = "Missing or invalid promotionId",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing simulated identity headers or test token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User or promotion not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Promotion is not current or is already associated",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public AssociatePromotionResponse associate(
            @Parameter(description = "Simulated client identifier for the request")
            @RequestHeader(name = "X-User-Id") Integer userId,
            @Valid @RequestBody AssociatePromotionRequest request) {
        return associatePromotionService.associate(userId, request);
    }
}
