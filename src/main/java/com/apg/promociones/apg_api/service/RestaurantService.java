package com.apg.promociones.apg_api.service;

import com.apg.promociones.apg_api.dto.CreateRestaurantRequest;
import com.apg.promociones.apg_api.dto.CreateRestaurantResponse;
import com.apg.promociones.apg_api.dto.RestaurantDTOs.RestaurantDetailResponse;
import com.apg.promociones.apg_api.dto.UpdateRestaurantRequest;
import com.apg.promociones.apg_api.dto.UpdateRestaurantResponse;
import com.apg.promociones.apg_api.exception.BadRequestException;
import com.apg.promociones.apg_api.exception.ResourceNotFoundException;
import com.apg.promociones.apg_api.exception.RestaurantStaffExceptions.PermisoDenegadoException;
import com.apg.promociones.apg_api.model.Restaurant;
import com.apg.promociones.apg_api.model.User;
import com.apg.promociones.apg_api.repository.RestaurantRepository;
import com.apg.promociones.apg_api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RestaurantService {

    private static final int MAX_NAME_LENGTH = 255;
    private static final int MAX_ADDRESS_LENGTH = 255;

    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;

    @Transactional
    public CreateRestaurantResponse create(Integer ownerId, CreateRestaurantRequest request) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        String desc = request.getDescription();
        String normalizedDescription = (desc == null || desc.isBlank()) ? null : desc.trim();

        LocalDateTime now = LocalDateTime.now();
        Restaurant restaurant = Restaurant.builder()
                .name(request.getName().trim())
                .owner(owner)
                .description(normalizedDescription)
                .fullAddress(request.getFullAddress().trim())
                .creationDate(now)
                .build();

        restaurant = restaurantRepository.save(restaurant);

        return CreateRestaurantResponse.builder()
                .restaurantId(restaurant.getId())
                .name(restaurant.getName())
                .ownerId(owner.getId())
                .description(restaurant.getDescription())
                .fullAddress(restaurant.getFullAddress())
                .creationDate(now)
                .message("Restaurante registrado exitosamente.")
                .build();
    }

    @Transactional(readOnly = true)
    public List<RestaurantDetailResponse> getMyRestaurants(Integer ownerId) {
        return restaurantRepository.findByOwnerIdOrderByCreationDateDesc(ownerId)
                .stream()
                .map(this::toDetailResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RestaurantDetailResponse getById(Integer restaurantId, Integer currentUserId) {
        if (restaurantId == null || restaurantId <= 0) {
            throw new BadRequestException("The restaurantId must be a positive integer.");
        }

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No se encontró el restaurante con ID: " + restaurantId));

        if (!restaurant.getOwner().getId().equals(currentUserId)) {
            throw new PermisoDenegadoException("No tienes permiso para ver el detalle de este restaurante.");
        }

        return toDetailResponse(restaurant);
    }

    private RestaurantDetailResponse toDetailResponse(Restaurant restaurant) {
        return RestaurantDetailResponse.builder()
                .restaurantId(restaurant.getId())
                .name(restaurant.getName())
                .ownerId(restaurant.getOwner().getId())
                .description(restaurant.getDescription())
                .fullAddress(restaurant.getFullAddress())
                .creationDate(restaurant.getCreationDate())
                .build();
    }
    /**
     * Partially updates editable fields (name, description, fullAddress) of an existing restaurant
     * after verifying that the authenticated caller owns it via {@code restaurant_owner_id}.
     * Ownership cannot be reassigned through this operation.
     */
    @Transactional
    public UpdateRestaurantResponse update(
            Integer restaurantId,
            Integer authenticatedUserId,
            UpdateRestaurantRequest request) {
        if (restaurantId == null || restaurantId <= 0) {
            throw new BadRequestException("The restaurantId must be a positive integer.");
        }

        validateUpdateRequest(request);

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found."));

        Integer ownerId = restaurant.getOwner() != null ? restaurant.getOwner().getId() : null;
        if (ownerId == null || !ownerId.equals(authenticatedUserId)) {
            throw new AccessDeniedException(
                    "Acceso denegado: solo el propietario del restaurante puede actualizar su información.");
        }

        if (request.isNameProvided()) {
            restaurant.setName(request.getName().trim());
        }
        if (request.isDescriptionProvided()) {
            String desc = request.getDescription();
            restaurant.setDescription(desc == null || desc.isBlank() ? null : desc.trim());
        }
        if (request.isFullAddressProvided()) {
            restaurant.setFullAddress(request.getFullAddress().trim());
        }

        restaurant = restaurantRepository.saveAndFlush(restaurant);

        return UpdateRestaurantResponse.builder()
                .restaurantId(restaurant.getId())
                .name(restaurant.getName())
                .ownerId(ownerId)
                .description(restaurant.getDescription())
                .fullAddress(restaurant.getFullAddress())
                .creationDate(restaurant.getCreationDate())
                .message("Restaurante actualizado exitosamente.")
                .build();
    }

    private static void validateUpdateRequest(UpdateRestaurantRequest request) {
        if (request == null) {
            throw new BadRequestException(
                    "At least one editable field (name, description, or fullAddress) must be provided.");
        }
        if (!request.getUnknownFields().isEmpty()) {
            throw new BadRequestException("The following fields are not allowed or cannot be modified: "
                    + String.join(", ", request.getUnknownFields()) + ".");
        }
        if (!request.hasAnyEditableField()) {
            throw new BadRequestException(
                    "At least one editable field (name, description, or fullAddress) must be provided.");
        }
        if (request.isNameProvided()) {
            validateRequiredTextField(request.getName(), "name", MAX_NAME_LENGTH);
        }
        if (request.isFullAddressProvided()) {
            validateRequiredTextField(request.getFullAddress(), "fullAddress", MAX_ADDRESS_LENGTH);
        }
    }

    private static void validateRequiredTextField(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("The " + fieldName + " field must not be blank.");
        }
        if (value.trim().length() > maxLength) {
            throw new BadRequestException(
                    "The " + fieldName + " field must not exceed " + maxLength + " characters.");
        }
    }
}
