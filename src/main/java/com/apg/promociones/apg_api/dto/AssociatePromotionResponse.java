package com.apg.promociones.apg_api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssociatePromotionResponse {

    private Integer couponId;
    private Integer userId;
    private Integer promotionId;
    private String couponHash;
    private String status;
    private LocalDateTime claimTimestamp;
    private String message;
}
