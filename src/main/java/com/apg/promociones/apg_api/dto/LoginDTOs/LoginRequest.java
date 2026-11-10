package com.apg.promociones.apg_api.dto.LoginDTOs;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload for user login and JWT token issuance")
public class LoginRequest {

    @NotBlank(message = "Email is required.")
    @Email(message = "Invalid email format.")
    @Schema(
            description = "Registered email address of the user",
            example = "carlos.perez@example.com",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String email;

    @NotBlank(message = "Password is required.")
    @Schema(
            description = "Plain-text password to verify against the stored BCrypt hash",
            example = "Password123!",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String password;
}
