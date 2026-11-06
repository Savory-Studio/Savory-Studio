package com.apg.promociones.apg_api.controller;

import com.apg.promociones.apg_api.dto.AssociatePromotionRequest;
import com.apg.promociones.apg_api.dto.AssociatePromotionResponse;
import com.apg.promociones.apg_api.dto.ErrorResponse;
import com.apg.promociones.apg_api.dto.UserCouponResponse;
import com.apg.promociones.apg_api.exception.BadRequestException;
import com.apg.promociones.apg_api.exception.UnauthorizedException;
import com.apg.promociones.apg_api.security.SimulatedAuthProperties;
import com.apg.promociones.apg_api.security.SimulatedRequestContext;
import com.apg.promociones.apg_api.service.AssociatePromotionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Coupons", description = "Associate current promotions with the customer profile and list customer coupons")
public class CouponController {

    private final AssociatePromotionService associatePromotionService;
    private final SimulatedAuthProperties authProperties;

    @GetMapping({"/api/cupones/usuario/{userId}", "/api/coupons/usuario/{userId}"})
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Get coupons by user ID",
            description = "Retrieves all coupons (UNUSED and USED) associated with the specified customer profile to support the 'My Coupons' screen."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Customer coupons retrieved successfully",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserCouponResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Invalid userId parameter",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing simulated identity headers or test token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "The requested user differs from the simulated caller",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public List<UserCouponResponse> getUserCoupons(
            @Parameter(description = "Identifier of the customer whose coupons are requested")
            @PathVariable("userId") Integer userId) {
        return associatePromotionService.getUserCoupons(resolveCustomer(userId, null));
    }

    @PostMapping("/api/coupons")
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
            @ApiResponse(responseCode = "403", description = "The requested user differs from the simulated caller",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User or promotion not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Promotion is not current or is already associated",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public AssociatePromotionResponse associate(
            @Parameter(description = "Simulated client identifier for the request")
            @RequestHeader(name = "X-User-Id", required = false) Integer headerUserId,
            @Valid @RequestBody AssociatePromotionRequest request) {
        return associatePromotionService.associate(resolveCustomer(request.getUserId(), headerUserId), request);
    }

    private Integer resolveCustomer(Integer requestedId, Integer headerId) {
        if (requestedId != null && requestedId <= 0) {
            throw new BadRequestException("The userId must be a positive integer.");
        }
        if (authProperties.isEnabled()) {
            Integer callerId = SimulatedRequestContext.requireUserId();
            if (requestedId != null && !callerId.equals(requestedId)) {
                throw new AccessDeniedException("The requested user differs from the authenticated customer.");
            }
            return callerId;
        }
        Integer customerId = requestedId != null ? requestedId : headerId;
        if (customerId == null) {
            throw new UnauthorizedException("The X-User-Id header is missing.");
        }
        return customerId;
    }
}
