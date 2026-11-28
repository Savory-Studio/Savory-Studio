package com.apg.promociones.apg_api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload to register a new restaurant for the authenticated user")
public class CreateRestaurantRequest {

    @NotBlank(message = "The name field is required.")
    @Size(max = 255, message = "The name field must not exceed 255 characters.")
    @Schema(description = "Restaurant name", example = "La Cazuela Criolla", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "Restaurant description", example = "Restaurante de comida típica colombiana")
    private String description;

    @NotBlank(message = "The fullAddress field is required.")
    @Size(max = 255, message = "The fullAddress field must not exceed 255 characters.")
    @Schema(description = "Full street address", example = "Calle 45 #12-30, Bucaramanga", requiredMode = Schema.RequiredMode.REQUIRED)
    private String fullAddress;
}
