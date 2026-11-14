package com.apg.promociones.apg_api.dto.UserDTOs;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Coupon summary item returned in the customer's 'Mis Cupones' query")
public class UserCouponResponse {

    @Schema(description = "Unique coupon ID", example = "15")
    private Integer couponId;

    @Schema(description = "Customer user ID", example = "10")
    private Integer userId;

    @Schema(description = "Associated promotion (discount) ID", example = "20")
    private Integer promotionId;

    @Schema(description = "Display name of the promotion", example = "2x1 Lunch")
    private String promotionName;

    @Schema(description = "ID of the restaurant offering the promotion", example = "1")
    private Integer restaurantId;

    @Schema(description = "Name of the restaurant offering the promotion", example = "Savory Grill")
    private String restaurantName;

    @Schema(description = "Discount type (PERCENTAGE, FIXED_VALUE, IXJ)", example = "IXJ")
    private String type;

    @Schema(description = "Discount amount", example = "20.00")
    private BigDecimal amount;

    @Schema(description = "Units taken by customer (for IXJ)", example = "2")
    private Integer customerTakesAmount;

    @Schema(description = "Units paid by customer (for IXJ)", example = "1")
    private Integer customerPaysAmount;

    @Schema(description = "Unique UUID v4 hash for QR redemption", example = "a3f1b2c4-d5e6-7890-abcd-ef1234567890")
    private String couponHash;

    @Schema(description = "Coupon status (UNUSED or USED)", example = "UNUSED")
    private String status;

    @Schema(description = "Timestamp when the coupon was claimed", example = "2026-10-30T14:35:00")
    private LocalDateTime claimTimestamp;

    @Schema(description = "Timestamp when the coupon was redeemed, if USED", example = "2026-10-31T13:10:00")
    private LocalDateTime redemptionTimestamp;

    @Schema(description = "Promotion expiration timestamp", example = "2026-11-26T23:59:00")
    private LocalDateTime endTimestamp;

    @Schema(description = "Promotional image URL", example = "https://example.com/promo.jpg")
    private String imageUrl;
}
