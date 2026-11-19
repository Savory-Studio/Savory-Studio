package com.apg.promociones.apg_api.service;

import com.apg.promociones.apg_api.dto.RestaurantStaffDTOs.RestaurantStaffDTOs.AssignStaffRequest;
import com.apg.promociones.apg_api.dto.RestaurantStaffDTOs.RestaurantStaffDTOs.StaffMemberResponse;
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

        User employee = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe un usuario registrado con el correo: " + request.getEmail()));

        if (restaurantStaffRepository.existsByUserIdAndRestaurantId(employee.getId(), restaurantId)) {
            throw new EmpleadoYaVinculadoException("El usuario con correo " + request.getEmail() + " ya es empleado de este restaurante.");
        }

        RestaurantStaff newStaff = RestaurantStaff.builder()
                .restaurant(restaurant)
                .user(employee)
                .assignedAt(LocalDateTime.now())
                .build();

        restaurantStaffRepository.save(newStaff);

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
        getRestaurantAndVerifyOwner(restaurantId, ownerId);

        RestaurantStaff staffAssignment = restaurantStaffRepository.findByUserIdAndRestaurantId(userId, restaurantId)
                .orElseThrow(() -> new RecursoNoEncontradoException("El usuario especificado no es empleado de este restaurante."));

        restaurantStaffRepository.delete(staffAssignment);
    }

    private Restaurant getRestaurantAndVerifyOwner(Integer restaurantId, Integer currentUserId) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró el restaurante con ID: " + restaurantId));

        if (!restaurant.getOwner().getId().equals(currentUserId)) {
            throw new PermisoDenegadoException("Acceso denegado. Solo el dueño del restaurante puede gestionar el personal.");
        }

        return restaurant;
    }
}