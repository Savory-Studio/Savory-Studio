package com.apg.promociones.apg_api.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CanjeRequest {

    private Integer couponId;

    private String couponHash;
}
