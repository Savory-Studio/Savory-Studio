package com.apg.promociones.apg_api.controller;

import com.apg.promociones.apg_api.config.OpenApiConfig;
import com.apg.promociones.apg_api.dto.ChangePasswordRequest;
import com.apg.promociones.apg_api.dto.ChangePasswordResponse;
import com.apg.promociones.apg_api.dto.ErrorResponse;
import com.apg.promociones.apg_api.exception.UnauthorizedException;
import com.apg.promociones.apg_api.security.SecurityUtils;
import com.apg.promociones.apg_api.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints for managing the authenticated user's account and credentials.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Authenticated user account and credential management endpoints")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
public class UserController {

    private final AuthService authService;

    @PatchMapping("/me/password")
    @Operation(
            summary = "Change authenticated user's password",
            description = "Verifies the current password and replaces the stored BCrypt hash with the new password. "
                    + "The user is identified exclusively from the authenticated Bearer token."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Password updated successfully",
                    content = @Content(schema = @Schema(implementation = ChangePasswordResponse.class))),
            @ApiResponse(responseCode = "400", description = "Incorrect current password, invalid new password, or new password matches current",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or expired Bearer token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Authenticated user account not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ChangePasswordResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        Integer userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException(
                        "Could not identify the authenticated user."));
        ChangePasswordResponse response = authService.changePassword(userId, request);
        return ResponseEntity.ok(response);
    }
}
