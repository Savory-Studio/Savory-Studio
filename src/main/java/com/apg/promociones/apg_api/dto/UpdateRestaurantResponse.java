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
@Schema(description = "Response payload returned after successfully updating a restaurant")
public class UpdateRestaurantResponse {

    @Schema(description = "Restaurant ID", example = "1")
    private Integer restaurantId;

    @Schema(description = "Updated restaurant name", example = "La Cazuela Criolla Gourmet")
    private String name;

    @Schema(description = "Owner user ID (immutable via PATCH)", example = "3")
    private Integer ownerId;

    @Schema(description = "Updated restaurant description", example = "Cocina típica colombiana renovada")
    private String description;

    @Schema(description = "Updated full street address", example = "Carrera 27 #34-10, Bucaramanga")
    private String fullAddress;

    @Schema(description = "Original timestamp when the restaurant was created", example = "2026-11-13T10:00:00")
    private LocalDateTime creationDate;

    @Schema(description = "Confirmation message", example = "Restaurante actualizado exitosamente.")
    private String message;
}
