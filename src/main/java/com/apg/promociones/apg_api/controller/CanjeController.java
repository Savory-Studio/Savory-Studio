package com.apg.promociones.apg_api.controller;

import com.apg.promociones.apg_api.config.OpenApiConfig;
import com.apg.promociones.apg_api.dto.CanjeRequest;
import com.apg.promociones.apg_api.dto.CanjeResponse;
import com.apg.promociones.apg_api.dto.ErrorResponse;
import com.apg.promociones.apg_api.service.CanjeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/canjes")
@RequiredArgsConstructor
@Tag(name = "Canjes", description = "Coupon redemption validation endpoint (requires JWT Bearer token)")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
public class CanjeController {

    private final CanjeService canjeService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Redeem a coupon",
            description = "Validates business rules (status, expiration, per-user and global usage limits) and registers the coupon redemption at the restaurant by couponHash (QR UUID) or couponId."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Coupon redeemed successfully",
                    content = @Content(schema = @Schema(implementation = CanjeResponse.class))),
            @ApiResponse(responseCode = "400", description = "Missing both couponId and couponHash",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or expired Bearer token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Coupon not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Business rule violation (already used, promotion expired/inactive, or limit reached)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public CanjeResponse redeem(@Valid @RequestBody CanjeRequest request) {
        return canjeService.redeem(request);
    }
}
