package com.apg.promociones.apg_api.dto.RestaurantDTOs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantDetailResponse {
    private Integer restaurantId;
    private String name;
    private Integer ownerId;
    private String description;
    private String fullAddress;
    private LocalDateTime creationDate;
}