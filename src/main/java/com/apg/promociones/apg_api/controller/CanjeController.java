package com.apg.promociones.apg_api.controller;

import com.apg.promociones.apg_api.dto.CanjeRequest;
import com.apg.promociones.apg_api.dto.CanjeResponse;
import com.apg.promociones.apg_api.dto.ErrorResponse;
import com.apg.promociones.apg_api.service.CanjeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/canjes")
@RequiredArgsConstructor
@Tag(name = "Canjes", description = "Coupon redemption validation endpoint")
public class CanjeController {

    private final CanjeService canjeService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Redeem a coupon",
            description = "Validates business rules and registers the coupon redemption at the restaurant."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Coupon redeemed successfully"),
            @ApiResponse(responseCode = "400", description = "Missing or invalid couponId",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Coupon not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Business rule violation",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public CanjeResponse redeem(@Valid @RequestBody CanjeRequest request) {
        return canjeService.redeem(request);
    }
}
