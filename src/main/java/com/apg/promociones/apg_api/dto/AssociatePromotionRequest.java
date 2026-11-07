package com.apg.promociones.apg_api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload to associate an active promotion with a customer profile")
public class AssociatePromotionRequest {

    @NotNull(message = "The userId field is required.")
    @Schema(description = "ID of the customer claiming the promotion", example = "10", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer userId;

    @NotNull(message = "The promotionId field is required.")
    @Schema(description = "ID of the promotion (discount) to associate", example = "20", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer promotionId;

    public AssociatePromotionRequest(Integer promotionId) {
        this.promotionId = promotionId;
    }
}
