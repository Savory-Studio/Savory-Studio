package com.apg.promociones.apg_api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PromocionRequest {

    @NotNull(message = "El campo restaurantId es obligatorio.")
    private Integer restaurantId;

    @NotBlank(message = "El campo name es obligatorio.")
    private String name;

    @NotBlank(message = "El campo applicableProducts es obligatorio.")
    private String applicableProducts;

    @NotBlank(message = "El campo type es obligatorio.")
    private String type;

    private BigDecimal amount;
    private Integer customerTakesAmount;
    private Integer customerPaysAmount;
    private LocalDateTime startTimestamp;
    private LocalDateTime endTimestamp;
    private Integer maxUses;
    private Integer maxUseAmountPerUser;
    private String imageUrl;
    private String additionalUseConditions;
}
