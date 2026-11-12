package com.apg.promociones.apg_api.controller;

import com.apg.promociones.apg_api.config.OpenApiConfig;
import com.apg.promociones.apg_api.dto.AssociatePromotionRequest;
import com.apg.promociones.apg_api.dto.AssociatePromotionResponse;
import com.apg.promociones.apg_api.dto.ErrorResponse;
import com.apg.promociones.apg_api.dto.UserCouponResponse;
import com.apg.promociones.apg_api.security.SecurityUtils;
import com.apg.promociones.apg_api.service.AssociatePromotionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/api/coupons", "/api/cupones"})
@RequiredArgsConstructor
@Tag(name = "Coupons", description = "Customer coupon management endpoints (requires JWT Bearer token)")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
public class CouponController {

    private final AssociatePromotionService associatePromotionService;

    @GetMapping("/usuario/{userId}")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Get user coupons (Mis Cupones)",
            description = "Retrieves all coupons (both UNUSED and USED) associated with the specified customer profile."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User coupons retrieved successfully",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserCouponResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Invalid userId parameter",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or expired Bearer token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated user does not match requested userId",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public List<UserCouponResponse> getUserCoupons(
            @Parameter(description = "ID of the customer whose coupons are requested", example = "10", required = true)
            @PathVariable("userId") Integer userId) {
        SecurityUtils.getCurrentUserId().ifPresent(authUserId -> {
            if (!authUserId.equals(userId)) {
                throw new AccessDeniedException("Acceso denegado: el usuario autenticado no coincide con el ID solicitado");
            }
        });
        return associatePromotionService.getUserCoupons(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Associate an active promotion with a customer",
            description = "Creates an UNUSED coupon linking the customer and the promotion after verifying that the promotion exists and is currently valid."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Promotion associated with the customer profile",
                    content = @Content(schema = @Schema(implementation = AssociatePromotionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Missing or invalid request data",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or expired Bearer token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated user does not match requested userId",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User or promotion does not exist",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Promotion is not active, out of date range, or already claimed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public AssociatePromotionResponse associatePromotion(@Valid @RequestBody AssociatePromotionRequest request) {
        SecurityUtils.getCurrentUserId().ifPresent(authUserId -> {
            if (request.getUserId() != null && !authUserId.equals(request.getUserId())) {
                throw new AccessDeniedException("Acceso denegado: no puede asociar promociones a otro usuario");
            }
        });
        Integer effectiveUserId = SecurityUtils.getCurrentUserId().orElse(request.getUserId());
        return associatePromotionService.associate(effectiveUserId, request);
    }
}
