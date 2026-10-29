package com.apg.promociones.apg_api.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromocionResponse {

    private Integer discountId;
    private Integer restaurantId;
    private String name;
    private String type;
    private BigDecimal amount;
    private String status;
    private LocalDateTime startTimestamp;
    private LocalDateTime endTimestamp;
    private Integer maxUses;
    private String message;
}
