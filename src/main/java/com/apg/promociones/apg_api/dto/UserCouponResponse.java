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
public class UserCouponResponse {

    private Integer couponId;
    private Integer userId;
    private Integer promotionId;
    private String promotionName;
    private Integer restaurantId;
    private String restaurantName;
    private String type;
    private BigDecimal amount;
    private Integer customerTakesAmount;
    private Integer customerPaysAmount;
    private String couponHash;
    private String status;
    private LocalDateTime claimTimestamp;
    private LocalDateTime redemptionTimestamp;
    private LocalDateTime endTimestamp;
    private String imageUrl;
}
