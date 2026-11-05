package com.apg.promociones.apg_api.dto.RegisterDTOs;

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

    @NotBlank(message = "El campo lastName es obligatorio.")
    @Size(max = 50, message = "El apellido no puede exceder los 50 caracteres.")
    private String lastName;

    @NotNull(message = "El campo birthDate es obligatorio.")
    private LocalDate birthDate;

    @NotBlank(message = "El campo email es obligatorio.")
    @Email(message = "El correo electrónico no tiene un formato válido.")
    private String email;

    @NotBlank(message = "El campo password es obligatorio.")
    @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres.")
    private String password;
}