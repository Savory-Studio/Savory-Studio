package com.apg.promociones.apg_api.controller;

import com.apg.promociones.apg_api.config.OpenApiConfig;
import com.apg.promociones.apg_api.dto.CreateRestaurantRequest;
import com.apg.promociones.apg_api.dto.CreateRestaurantResponse;
import com.apg.promociones.apg_api.dto.ErrorResponse;
import com.apg.promociones.apg_api.dto.RestaurantDTOs.RestaurantDetailResponse;
import com.apg.promociones.apg_api.exception.UnauthorizedException;
import com.apg.promociones.apg_api.security.SecurityUtils;
import com.apg.promociones.apg_api.service.RestaurantService;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @GetMapping("/mios")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "List restaurants owned by the authenticated user",
            description = "Returns only the restaurants belonging to the authenticated owner identified via JWT."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of owned restaurants retrieved successfully",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = RestaurantDetailResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or expired Bearer token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public List<RestaurantDetailResponse> getMyRestaurants() {
        Integer ownerId = getAuthenticatedUserId();
        return restaurantService.getMyRestaurants(ownerId);
    }

    @GetMapping("/{restaurantId}")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Get restaurant detail by ID",
            description = "Returns the detail of a specific restaurant without sensitive data."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Restaurant detail retrieved successfully",
                    content = @Content(schema = @Schema(implementation = RestaurantDetailResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or expired Bearer token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated user does not have permission to view this restaurant",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Restaurant not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public RestaurantDetailResponse getById(@PathVariable Integer restaurantId) {
        Integer currentUserId = getAuthenticatedUserId();
        return restaurantService.getById(restaurantId, currentUserId);
    }

    private Integer getAuthenticatedUserId() {
        return SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException(
                        "Could not identify the authenticated user."));
    }
}
