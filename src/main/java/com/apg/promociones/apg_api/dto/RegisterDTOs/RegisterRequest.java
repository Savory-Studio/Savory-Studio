package com.apg.promociones.apg_api.dto.RegisterDTOs;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload to register a new user account")
public class RegisterRequest {

    @NotBlank(message = "El campo firstName es obligatorio.")
    @Size(max = 50, message = "El nombre no puede exceder los 50 caracteres.")
    @Schema(description = "First name of the user", example = "Carlos", requiredMode = Schema.RequiredMode.REQUIRED)
    private String firstName;

    @Size(max = 50, message = "El segundo nombre no puede exceder los 50 caracteres.")
    @Schema(description = "Optional middle or second name of the user", example = "Andrés")
    private String secondName;

    @NotBlank(message = "El campo lastName es obligatorio.")
    @Size(max = 50, message = "El apellido no puede exceder los 50 caracteres.")
    @Schema(description = "Last name of the user", example = "Pérez", requiredMode = Schema.RequiredMode.REQUIRED)
    private String lastName;

    @NotNull(message = "El campo birthday es obligatorio.")
    @JsonAlias("birthDate")
    @Schema(description = "Date of birth in ISO-8601 format (YYYY-MM-DD)", example = "1995-05-15", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate birthday;

    @NotBlank(message = "El campo email es obligatorio.")
    @Email(message = "El correo electrónico no tiene un formato válido.")
    @Size(max = 255, message = "El correo no puede exceder los 255 caracteres.")
    @Schema(description = "Unique email address of the user", example = "carlos.perez@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @NotBlank(message = "El campo password es obligatorio.")
    @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres.")
    @Schema(description = "Password (8 to 72 UTF-8 bytes, stored as BCrypt hash)", example = "Password123!", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;
}
