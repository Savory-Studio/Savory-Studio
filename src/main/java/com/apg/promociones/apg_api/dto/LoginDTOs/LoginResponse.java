package com.apg.promociones.apg_api.dto.LoginDTOs;

import com.apg.promociones.apg_api.model.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response payload returned upon successful user authentication containing the signed JWT token")
public class LoginResponse {

    @Schema(
            description = "Signed HS256 JWT token to be sent in the Authorization: Bearer <token> header",
            example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMiIsImVtYWlsIjoiY2FybG9zLnBlcmV6QGV4YW1wbGUuY29tIn0.signature"
    )
    private String token;

    @Builder.Default
    @Schema(description = "Authentication scheme type", example = "Bearer")
    private String tokenType = "Bearer";

    @Schema(description = "Token validity duration", example = "86400 seconds")
    private String expiresIn;

    @Schema(description = "Unique identifier of the authenticated user", example = "12")
    private Integer userId;

    @Schema(description = "Email address of the authenticated user", example = "carlos.perez@example.com")
    private String email;

    @Schema(description = "First name of the authenticated user", example = "Carlos")
    private String firstName;

    @Schema(description = "Last name of the authenticated user", example = "Pérez")
    private String lastName;

    @Schema(description = "Human-readable status message", example = "Authentication successful.")
    private String message;

    public static LoginResponse of(User user, String token, String expiresIn) {
        return LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(expiresIn + " seconds")
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .message("Authentication successful.")
                .build();
    }
}
