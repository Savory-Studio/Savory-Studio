package com.apg.promociones.apg_api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response payload returned after updating the authenticated user's password")
public class ChangePasswordResponse {

    @Schema(description = "Unique identifier of the authenticated user", example = "12")
    private Integer userId;

    @Schema(description = "Email address of the authenticated user", example = "carlos.perez@example.com")
    private String email;

    @Schema(description = "Confirmation message", example = "Password updated successfully.")
    private String message;
}
