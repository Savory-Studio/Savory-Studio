package com.apg.promociones.apg_api.controller;

import com.apg.promociones.apg_api.config.OpenApiConfig;
import com.apg.promociones.apg_api.dto.CreateRestaurantRequest;
import com.apg.promociones.apg_api.dto.CreateRestaurantResponse;
import com.apg.promociones.apg_api.dto.ErrorResponse;
import com.apg.promociones.apg_api.exception.UnauthorizedException;
import com.apg.promociones.apg_api.security.SecurityUtils;
import com.apg.promociones.apg_api.service.RestaurantService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/restaurantes")
@RequiredArgsConstructor
@Tag(name = "Restaurants", description = "Restaurant management endpoints (requires JWT Bearer token)")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
public class RestaurantController {

    private final RestaurantService restaurantService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Register a new restaurant",
            description = "Creates a restaurant owned by the authenticated user. The owner is identified via JWT, never via a client-provided ID."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Restaurant created successfully",
                    content = @Content(schema = @Schema(implementation = CreateRestaurantResponse.class))),
            @ApiResponse(responseCode = "400", description = "Missing or invalid request data (name or address)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or expired Bearer token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public CreateRestaurantResponse create(@Valid @RequestBody CreateRestaurantRequest request) {
        Integer ownerId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException(
                        "Could not identify the authenticated user."));
        return restaurantService.create(ownerId, request);
    }
}
