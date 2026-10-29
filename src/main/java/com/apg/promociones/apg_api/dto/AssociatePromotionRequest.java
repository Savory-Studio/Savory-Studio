package com.apg.promociones.apg_api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AssociatePromotionRequest {

    @NotNull(message = "The userId field is required.")
    private Integer userId;

    @NotNull(message = "The promotionId field is required.")
    private Integer promotionId;
}
