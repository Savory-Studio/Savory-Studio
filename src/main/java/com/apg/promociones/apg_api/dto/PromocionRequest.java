package com.apg.promociones.apg_api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload to publish a new promotion (discount)")
public class PromocionRequest {

    @NotNull(message = "El campo restaurantId es obligatorio.")
    @Schema(description = "ID of the restaurant publishing the promotion", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer restaurantId;

    @NotBlank(message = "El campo name es obligatorio.")
    @Schema(description = "Display name of the promotion", example = "Almuerzo 20% off", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotBlank(message = "El campo applicableProducts es obligatorio.")
    @Schema(description = "Products or menu items covered by the promotion", example = "Todos los platos del menú ejecutivo", requiredMode = Schema.RequiredMode.REQUIRED)
    private String applicableProducts;

    @NotBlank(message = "El campo type es obligatorio.")
    @Schema(description = "Discount type: PERCENTAGE, FIXED_VALUE, or IXJ", example = "PERCENTAGE", requiredMode = Schema.RequiredMode.REQUIRED)
    private String type;

    @Schema(description = "Discount value (required for PERCENTAGE and FIXED_VALUE)", example = "20.00")
    private BigDecimal amount;

    @Schema(description = "Quantity taken by customer (required for IXJ, must be > customerPaysAmount)", example = "2")
    private Integer customerTakesAmount;

    @Schema(description = "Quantity paid by customer (required for IXJ, must be > 0)", example = "1")
    private Integer customerPaysAmount;

    @Schema(description = "Promotion start date/time (ISO-8601)", example = "2026-10-26T08:00:00")
    private LocalDateTime startTimestamp;

    @Schema(description = "Promotion expiration date/time (at least one of endTimestamp or maxUses is required)", example = "2026-11-26T23:59:00")
    private LocalDateTime endTimestamp;

    @Min(value = 1, message = "El límite de usos debe ser mayor a 0.")
    @Schema(description = "Maximum total redemptions allowed (at least one of endTimestamp or maxUses is required)", example = "100")
    private Integer maxUses;

    @Min(value = 1, message = "El límite de usos por usuario debe ser mayor a 0.")
    @Schema(description = "Maximum redemptions allowed per customer", example = "2")
    private Integer maxUseAmountPerUser;

    @Schema(description = "Optional promotional image URL", example = "https://example.com/promo.jpg")
    private String imageUrl;

    @Schema(description = "Optional additional terms and conditions", example = "Válido solo para almuerzo")
    private String additionalUseConditions;

    @JsonIgnore
    @AssertTrue(message = "La fecha de inicio debe ser anterior a la fecha de vencimiento.")
    public boolean isDateRangeValid() {
        return startTimestamp == null || endTimestamp == null || startTimestamp.isBefore(endTimestamp);
    }
}
