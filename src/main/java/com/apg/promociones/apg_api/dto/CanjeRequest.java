package com.apg.promociones.apg_api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload to redeem a coupon by QR UUID hash or numeric ID (at least one is required)")
public class CanjeRequest {

    @Schema(description = "Numeric identifier of the coupon (optional when couponHash is provided)", example = "2")
    private Integer couponId;

    @Schema(description = "Unique UUID v4 hash from the coupon QR code (takes precedence over couponId)", example = "a3f1b2c4-d5e6-7890-abcd-ef1234567890")
    private String couponHash;
}
