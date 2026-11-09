package com.apg.promociones.apg_api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Response payload returned when a customer claims an active promotion")
public class AssociatePromotionResponse {

    @Schema(description = "Generated coupon ID", example = "15")
    private Integer couponId;

    @Schema(description = "Customer user ID", example = "3")
    private Integer userId;

    @Schema(description = "Claimed promotion (discount) ID", example = "9")
    private Integer promotionId;

    @Schema(description = "Unique UUID v4 hash generated for QR redemption", example = "a3f1b2c4-d5e6-7890-abcd-ef1234567890")
    private String couponHash;

    @Schema(description = "Initial coupon status", example = "UNUSED")
    private String status;

    @Schema(description = "Server timestamp when the coupon was claimed", example = "2026-10-30T14:35:00")
    private LocalDateTime claimTimestamp;

    @Schema(description = "Confirmation message", example = "Promoción guardada en el perfil del cliente.")
    private String message;
}
