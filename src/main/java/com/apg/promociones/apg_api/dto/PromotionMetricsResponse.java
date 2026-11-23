package com.apg.promociones.apg_api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Aggregate metrics for a single promotion")
public class PromotionMetricsResponse {

    @Schema(description = "Promotion ID", example = "1")
    private Integer promotionId;

    @Schema(description = "Promotion name", example = "Almuerzo 20% off")
    private String promotionName;

    @Schema(description = "Restaurant ID that owns this promotion", example = "1")
    private Integer restaurantId;

    @Schema(description = "Total detail views recorded for this promotion", example = "42")
    private Integer viewCount;

    @Schema(description = "Total coupons claimed (all statuses)", example = "10")
    private Long totalClaimed;

    @Schema(description = "Coupons effectively redeemed (USED)", example = "3")
    private Long totalRedeemed;
}
