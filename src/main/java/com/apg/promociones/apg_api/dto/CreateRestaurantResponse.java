package com.apg.promociones.apg_api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Response payload returned after successfully creating a restaurant")
public class CreateRestaurantResponse {

    @Schema(description = "Generated restaurant ID", example = "1")
    private Integer restaurantId;

    @Schema(description = "Restaurant name", example = "La Cazuela Criolla")
    private String name;

    @Schema(description = "Owner user ID", example = "3")
    private Integer ownerId;

    @Schema(description = "Restaurant description", example = "Restaurante de comida típica colombiana")
    private String description;

    @Schema(description = "Full street address", example = "Calle 45 #12-30, Bucaramanga")
    private String fullAddress;

    @Schema(description = "Server timestamp when the restaurant was created", example = "2026-11-13T10:00:00")
    private LocalDateTime creationDate;

    @Schema(description = "Confirmation message", example = "Restaurante registrado exitosamente.")
    private String message;
}
