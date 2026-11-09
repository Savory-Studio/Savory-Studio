package com.apg.promociones.apg_api.dto;

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
@Schema(description = "Active promotion item returned in the public catalog")
public class ActivePromotionResponse {

    @Schema(description = "Unique identifier of the promotion", example = "5")
    private Integer discountId;

    @Schema(description = "Identifier of the restaurant offering the promotion", example = "1")
    private Integer restaurantId;

    @Schema(description = "Name of the restaurant offering the promotion", example = "Savory Grill")
    private String restaurantName;

    @Schema(description = "Display name of the promotion", example = "Lunch 20% off")
    private String name;

    @Schema(description = "Products covered by the promotion", example = "Executive menu")
    private String applicableProducts;

    @Schema(description = "Discount type (PERCENTAGE, FIXED_VALUE, IXJ)", example = "PERCENTAGE")
    private String type;

    @Schema(description = "Discount amount for PERCENTAGE or FIXED_VALUE", example = "20.00")
    private BigDecimal amount;

    @Schema(description = "Units taken by customer for IXJ promotions", example = "2")
    private Integer customerTakesAmount;

    @Schema(description = "Units paid by customer for IXJ promotions", example = "1")
    private Integer customerPaysAmount;

    @Schema(description = "Current promotion status", example = "Active")
    private String status;

    @Schema(description = "Promotion start timestamp", example = "2026-10-26T08:00:00")
    private LocalDateTime startTimestamp;

    @Schema(description = "Promotion expiration timestamp", example = "2026-11-26T23:59:00")
    private LocalDateTime endTimestamp;

    @Schema(description = "Maximum total redemptions allowed", example = "100")
    private Integer maxUses;

    @Schema(description = "Maximum redemptions allowed per customer", example = "2")
    private Integer maxUseAmountPerUser;

    @Schema(description = "Promotional image URL", example = "https://example.com/promo.jpg")
    private String imageUrl;

    @Schema(description = "Additional usage conditions", example = "Valid Monday through Friday")
    private String additionalUseConditions;
}
