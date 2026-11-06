package com.apg.promociones.apg_api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivePromotionResponse {

    private Integer discountId;
    private Integer restaurantId;
    private String restaurantName;
    private String name;
    private String applicableProducts;
    private String type;
    private BigDecimal amount;
    private Integer customerTakesAmount;
    private Integer customerPaysAmount;
    private String status;
    private LocalDateTime startTimestamp;
    private LocalDateTime endTimestamp;
    private Integer maxUses;
    private Integer maxUseAmountPerUser;
    private String imageUrl;
    private String additionalUseConditions;
}
