package com.apg.promociones.apg_api.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CanjeResponse {

    private Integer couponId;
    private String status;
    private LocalDateTime redemptionTimestamp;
    private String message;
}
