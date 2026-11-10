package com.apg.promociones.apg_api.dto.RegisterDTOs;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Public profile returned after successful user registration")
public class RegisterResponse {

    @Schema(description = "Generated unique identifier of the user", example = "12")
    private Integer id;

    @Schema(description = "First name of the user", example = "Carlos")
    private String firstName;

    @Schema(description = "Second name of the user, if provided", example = "Andrés")
    private String secondName;

    @Schema(description = "Last name of the user", example = "Pérez")
    private String lastName;

    @Schema(description = "Date of birth (YYYY-MM-DD)", example = "1995-05-15")
    private LocalDate birthday;

    @Schema(description = "Registered email address", example = "carlos.perez@example.com")
    private String email;

    @Schema(description = "Server timestamp when the account was created", example = "2026-11-04T19:00:00")
    private LocalDateTime joinDate;
}
