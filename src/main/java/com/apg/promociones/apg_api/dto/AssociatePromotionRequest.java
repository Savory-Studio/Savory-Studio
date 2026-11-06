package com.apg.promociones.apg_api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
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
public class AssociatePromotionRequest {

    @Schema(description = "Optional customer ID. Must match the caller while simulated authentication is enabled.", example = "1")
    private Integer userId;

    @NotNull(message = "The promotionId field is required.")
    @Schema(description = "Identifier of the promotion to claim", example = "1")
    private Integer promotionId;

    public AssociatePromotionRequest(Integer promotionId) {
        this.promotionId = promotionId;
    }
}
