package com.apg.promociones.apg_api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload to change the authenticated user's password")
public class ChangePasswordRequest {

    @NotBlank(message = "Current password is required.")
    @JsonAlias("oldPassword")
    @Schema(
            description = "Current plain-text password to verify against the stored BCrypt hash",
            example = "Password123!",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String currentPassword;

    @NotBlank(message = "New password is required.")
    @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres.")
    @Schema(
            description = "New plain-text password (8 to 72 UTF-8 bytes, stored as BCrypt hash)",
            example = "NewPassword456!",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String newPassword;
}
