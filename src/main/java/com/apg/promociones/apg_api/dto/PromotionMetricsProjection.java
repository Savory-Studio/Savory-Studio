package com.apg.promociones.apg_api.dto;

public interface PromotionMetricsProjection {

    Integer getPromotionId();

    String getPromotionName();

    Integer getRestaurantId();

    Integer getViewCount();

    Long getTotalClaimed();

    Long getTotalRedeemed();
}
