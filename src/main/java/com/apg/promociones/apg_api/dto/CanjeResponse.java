package com.apg.promociones.apg_api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Response payload returned after a successful coupon redemption")
public class CanjeResponse {

    @Schema(description = "Numeric identifier of the redeemed coupon", example = "2")
    private Integer couponId;

    @Schema(description = "Unique UUID v4 hash of the redeemed coupon", example = "a3f1b2c4-d5e6-7890-abcd-ef1234567890")
    private String couponHash;

    @Schema(description = "Updated coupon status", example = "USED")
    private String status;

    @Schema(description = "Timestamp when the coupon was redeemed", example = "2026-10-30T14:35:00")
    private LocalDateTime redemptionTimestamp;

    @Schema(description = "Confirmation message", example = "Cupón canjeado exitosamente.")
    private String message;
}
