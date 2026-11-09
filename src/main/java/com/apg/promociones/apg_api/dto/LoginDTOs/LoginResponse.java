package com.apg.promociones.apg_api.dto.LoginDTOs;

import com.apg.promociones.apg_api.model.User;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private String token;
    @Builder.Default
    private String tokenType = "Bearer";
    private String expiresIn;
    private Integer userId;
    private String email;
    private String firstName;
    private String lastName;
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