package com.apg.promociones.apg_api.service;

import com.apg.promociones.apg_api.dto.RestaurantStaffDTOs.RestaurantStaffDTOs.AssignStaffRequest;
import com.apg.promociones.apg_api.dto.RestaurantStaffDTOs.RestaurantStaffDTOs.StaffMemberResponse;
import com.apg.promociones.apg_api.exception.BadRequestException;
import com.apg.promociones.apg_api.exception.RestaurantStaffExceptions.EmpleadoYaVinculadoException;
import com.apg.promociones.apg_api.exception.RestaurantStaffExceptions.PermisoDenegadoException;
import com.apg.promociones.apg_api.exception.RestaurantStaffExceptions.RecursoNoEncontradoException;
import com.apg.promociones.apg_api.model.Restaurant;
import com.apg.promociones.apg_api.model.RestaurantStaff;
import com.apg.promociones.apg_api.model.User;
import com.apg.promociones.apg_api.repository.RestaurantRepository;
import com.apg.promociones.apg_api.repository.RestaurantStaffRepository;
import com.apg.promociones.apg_api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RestaurantStaffService {

    private final RestaurantStaffRepository restaurantStaffRepository;
    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;

    @Transactional
    public StaffMemberResponse assignStaff(Integer restaurantId, Integer ownerId, AssignStaffRequest request) {
        Restaurant restaurant = getRestaurantAndVerifyOwner(restaurantId, ownerId);

        String normalizedEmail = request.getEmail().trim();
        User employee = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un usuario registrado con el correo: " + normalizedEmail));

        if (employee.getId().equals(ownerId)) {
            throw new BadRequestException(
                    "El propietario del restaurante ya cuenta con permisos y no puede vincularse como empleado.");
        }

        if (restaurantStaffRepository.existsByUserIdAndRestaurantId(employee.getId(), restaurantId)) {
            throw new EmpleadoYaVinculadoException(
                    "El usuario con correo " + normalizedEmail + " ya es empleado de este restaurante.");
        }

        RestaurantStaff newStaff = RestaurantStaff.builder()
                .restaurant(restaurant)
                .user(employee)
                .assignedAt(LocalDateTime.now())
                .build();

        try {
            newStaff = restaurantStaffRepository.saveAndFlush(newStaff);
        } catch (DataIntegrityViolationException ex) {
            throw new EmpleadoYaVinculadoException(
                    "El usuario con correo " + normalizedEmail + " ya es empleado de este restaurante.");
        }

        return StaffMemberResponse.builder()
                .userId(employee.getId())
                .email(employee.getEmail())
                .assignedAt(newStaff.getAssignedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public List<StaffMemberResponse> getStaffMembers(Integer restaurantId, Integer ownerId) {
        getRestaurantAndVerifyOwner(restaurantId, ownerId);

        return restaurantStaffRepository.findByRestaurantId(restaurantId).stream()
                .map(staff -> StaffMemberResponse.builder()
                        .userId(staff.getUser().getId())
                        .email(staff.getUser().getEmail())
                        .assignedAt(staff.getAssignedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public void removeStaff(Integer restaurantId, Integer ownerId, Integer userId) {
        if (userId == null || userId <= 0) {
            throw new BadRequestException("El userId debe ser un entero positivo.");
        }
        getRestaurantAndVerifyOwner(restaurantId, ownerId);

        RestaurantStaff staffAssignment = restaurantStaffRepository.findByUserIdAndRestaurantId(userId, restaurantId)
                .orElseThrow(() -> new RecursoNoEncontradoException("El usuario especificado no es empleado de este restaurante."));

        restaurantStaffRepository.delete(staffAssignment);
    }

    private Restaurant getRestaurantAndVerifyOwner(Integer restaurantId, Integer currentUserId) {
        if (restaurantId == null || restaurantId <= 0) {
            throw new BadRequestException("El restaurantId debe ser un entero positivo.");
        }
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró el restaurante con ID: " + restaurantId));

        if (!restaurant.getOwner().getId().equals(currentUserId)) {
            throw new PermisoDenegadoException("Acceso denegado. Solo el dueño del restaurante puede gestionar el personal.");
        }

        return restaurant;
    }
}