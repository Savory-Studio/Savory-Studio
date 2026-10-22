package com.apg.promociones.apg_api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CanjeRequest {

    @NotNull(message = "El campo couponId es obligatorio.")
    private Integer couponId;
}
