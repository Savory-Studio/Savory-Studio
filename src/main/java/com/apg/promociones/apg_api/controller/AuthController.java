package com.apg.promociones.apg_api.controller;

import com.apg.promociones.apg_api.dto.ErrorResponse;
import com.apg.promociones.apg_api.dto.LoginDTOs.LoginRequest;
import com.apg.promociones.apg_api.dto.LoginDTOs.LoginResponse;
import com.apg.promociones.apg_api.dto.PasswordRecoveryDTOs.ForgotPasswordRequest;
import com.apg.promociones.apg_api.dto.PasswordRecoveryDTOs.PasswordRecoveryResponse;
import com.apg.promociones.apg_api.dto.PasswordRecoveryDTOs.ResetPasswordRequest;
import com.apg.promociones.apg_api.dto.RegisterDTOs.RegisterRequest;
import com.apg.promociones.apg_api.dto.RegisterDTOs.RegisterResponse;
import com.apg.promociones.apg_api.service.AuthService;
import com.apg.promociones.apg_api.service.PasswordRecovery.PasswordRecoveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Public endpoints for user registration and JWT authentication")
public class AuthController {

    private final AuthService authService;
    private final PasswordRecoveryService passwordRecoveryService;

    public AuthController(AuthService authService, PasswordRecoveryService passwordRecoveryService) {
        this.authService = authService;
        this.passwordRecoveryService = passwordRecoveryService;
    }

    @PostMapping("/register")
    @SecurityRequirements()
    @Operation(
            summary = "Register a new user",
            description = "Public endpoint that creates a new user account with BCrypt password hashing. Does not require a Bearer token."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "User registered successfully",
                    content = @Content(schema = @Schema(implementation = RegisterResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input or validation constraint violated",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Email already registered",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    @SecurityRequirements()
    @Operation(
            summary = "User login & JWT issuance",
            description = "Public endpoint that authenticates user credentials (email and password) and issues a signed HS256 JWT Bearer token."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Authentication successful — returns signed JWT token",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request body, missing fields, or invalid email format",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Invalid credentials (email not found or wrong password — RN-17 generic message)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/forgot-password")
    @SecurityRequirements()
    @Operation(
            summary = "Request password recovery email",
            description = "Generates a single-use, time-limited reset token and sends it via email. Always returns 200 OK with a generic message to avoid email enumeration."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Generic confirmation response",
                    content = @Content(schema = @Schema(implementation = PasswordRecoveryResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid email format or missing field",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PasswordRecoveryResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok(passwordRecoveryService.requestPasswordReset(request));
    }

    @PostMapping("/reset-password")
    @SecurityRequirements()
    @Operation(
            summary = "Reset password using recovery token",
            description = "Validates the single-use recovery token, updates the user's password with BCrypt, and invalidates the token."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Password updated successfully",
                    content = @Content(schema = @Schema(implementation = PasswordRecoveryResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid, expired, or already-used token, or invalid new password",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PasswordRecoveryResponse> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(passwordRecoveryService.resetPassword(request));
    }
}
