package com.apg.promociones.apg_api.dto.RestaurantStaffDTOs;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

public class RestaurantStaffDTOs {

    @Data
    public static class AssignStaffRequest {
        @NotBlank(message = "El correo electrónico es obligatorio")
        @Email(message = "El formato del correo es inválido")
        private String email;
    }

    @Data
    @Builder
    public static class StaffMemberResponse {
        private Integer userId;
        private String email;
        private LocalDateTime assignedAt;
        // Puedes agregar firstName o lastName aquí si tu entidad User los tiene
    }
}