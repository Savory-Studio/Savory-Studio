package com.apg.promociones.apg_api.dto.RegisterDTOs;

import com.fasterxml.jackson.annotation.JsonAlias;
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
public class RegisterRequest {

    @NotBlank(message = "El campo firstName es obligatorio.")
    @Size(max = 50, message = "El nombre no puede exceder los 50 caracteres.")
    private String firstName;

    @Size(max = 50, message = "El segundo nombre no puede exceder los 50 caracteres.")
    private String secondName;

    @NotBlank(message = "El campo lastName es obligatorio.")
    @Size(max = 50, message = "El apellido no puede exceder los 50 caracteres.")
    private String lastName;

    @NotNull(message = "El campo birthday es obligatorio.")
    @JsonAlias("birthDate")
    private LocalDate birthday;

    @NotBlank(message = "El campo email es obligatorio.")
    @Email(message = "El correo electrónico no tiene un formato válido.")
    @Size(max = 255, message = "El correo no puede exceder los 255 caracteres.")
    private String email;

    @NotBlank(message = "El campo password es obligatorio.")
    @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres.")
    private String password;
}
