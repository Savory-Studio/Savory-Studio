package com.apg.promociones.apg_api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Response payload returned after publishing a new promotion")
public class PromocionResponse {

    @Schema(description = "Generated promotion (discount) ID", example = "6")
    private Integer discountId;

    @Schema(description = "ID of the owning restaurant", example = "1")
    private Integer restaurantId;

    @Schema(description = "Display name of the promotion", example = "Almuerzo 20% off")
    private String name;

    @Schema(description = "Discount type", example = "PERCENTAGE")
    private String type;

    @Schema(description = "Discount amount", example = "20.00")
    private BigDecimal amount;

    @Schema(description = "Initial promotion status", example = "Active")
    private String status;

    @Schema(description = "Promotion start timestamp", example = "2026-10-26T08:00:00")
    private LocalDateTime startTimestamp;

    @Schema(description = "Promotion expiration timestamp", example = "2026-11-26T23:59:00")
    private LocalDateTime endTimestamp;

    @Schema(description = "Maximum total redemptions allowed", example = "100")
    private Integer maxUses;

    @Schema(description = "Confirmation message", example = "Promoción creada exitosamente.")
    private String message;
}
