package com.apg.promociones.apg_api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
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

    @Min(value = 1, message = "El límite de usos debe ser mayor a 0.")
    private Integer maxUses;

    @Min(value = 1, message = "El límite de usos por usuario debe ser mayor a 0.")
    private Integer maxUseAmountPerUser;
    private String imageUrl;
    private String additionalUseConditions;

    @JsonIgnore
    @AssertTrue(message = "La fecha de inicio debe ser anterior a la fecha de vencimiento.")
    public boolean isDateRangeValid() {
        return startTimestamp == null || endTimestamp == null || startTimestamp.isBefore(endTimestamp);
    }
}
