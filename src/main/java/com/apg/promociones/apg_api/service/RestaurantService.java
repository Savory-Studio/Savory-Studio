package com.apg.promociones.apg_api.service;

import com.apg.promociones.apg_api.dto.CreateRestaurantRequest;
import com.apg.promociones.apg_api.dto.CreateRestaurantResponse;
import com.apg.promociones.apg_api.exception.ResourceNotFoundException;
import com.apg.promociones.apg_api.model.Restaurant;
import com.apg.promociones.apg_api.model.User;
import com.apg.promociones.apg_api.repository.RestaurantRepository;
import com.apg.promociones.apg_api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class RestaurantService {

    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;

    @Transactional
    public CreateRestaurantResponse create(Integer ownerId, CreateRestaurantRequest request) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        LocalDateTime now = LocalDateTime.now();
        Restaurant restaurant = Restaurant.builder()
                .name(request.getName())
                .owner(owner)
                .description(request.getDescription())
                .fullAddress(request.getFullAddress())
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
}
