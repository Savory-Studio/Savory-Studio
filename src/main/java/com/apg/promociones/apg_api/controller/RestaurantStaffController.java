package com.apg.promociones.apg_api.controller;

import com.apg.promociones.apg_api.config.OpenApiConfig;
import com.apg.promociones.apg_api.dto.RestaurantStaffDTOs.RestaurantStaffDTOs.AssignStaffRequest;
import com.apg.promociones.apg_api.dto.RestaurantStaffDTOs.RestaurantStaffDTOs.StaffMemberResponse;
import com.apg.promociones.apg_api.exception.UnauthorizedException;
import com.apg.promociones.apg_api.security.SecurityUtils;
import com.apg.promociones.apg_api.service.RestaurantStaffService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/restaurants/{restaurantId}/staff")
@RequiredArgsConstructor
@Tag(name = "Restaurant Staff", description = "Endpoints para la gestión de personal de restaurantes (Solo dueños)")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME) // Para documentación Swagger,
// permite indicarle al usuario que primero hay que ingresar el bearer token para usar los endpoints a continuación
public class RestaurantStaffController {

    private final RestaurantStaffService staffService;

    @PostMapping
    @Operation(summary = "Vincular un empleado", description = "Agrega un usuario existente como empleado del restaurante usando su email")
    public ResponseEntity<StaffMemberResponse> assignStaff(
            @PathVariable Integer restaurantId,
            @Valid @RequestBody AssignStaffRequest request) {

        Integer currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("Usuario no autenticado"));

        StaffMemberResponse response = staffService.assignStaff(restaurantId, currentUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "Listar empleados", description = "Obtiene la lista de empleados autorizados en el restaurante")
    public ResponseEntity<List<StaffMemberResponse>> getStaff(
            @PathVariable Integer restaurantId) {

        Integer currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("Usuario no autenticado"));

        return ResponseEntity.ok(staffService.getStaffMembers(restaurantId, currentUserId));
    }

    @DeleteMapping("/{userId}")
    @Operation(summary = "Desvincular un empleado", description = "Revoca el acceso de un empleado al restaurante")
    public ResponseEntity<Void> removeStaff(
            @PathVariable Integer restaurantId,
            @PathVariable Integer userId) {

        Integer currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("Usuario no autenticado"));

        staffService.removeStaff(restaurantId, currentUserId, userId);
        return ResponseEntity.noContent().build();
    }
}
