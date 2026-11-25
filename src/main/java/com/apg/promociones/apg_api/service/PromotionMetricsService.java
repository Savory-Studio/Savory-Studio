package com.apg.promociones.apg_api.service;

import com.apg.promociones.apg_api.dto.PromotionMetricsProjection;
import com.apg.promociones.apg_api.dto.PromotionMetricsResponse;
import com.apg.promociones.apg_api.exception.ResourceNotFoundException;
import com.apg.promociones.apg_api.repository.DiscountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PromotionMetricsService {

    private final DiscountRepository discountRepository;

    @Transactional(readOnly = true)
    public List<PromotionMetricsResponse> getAllMetrics() {
        return discountRepository.findPromotionMetrics().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PromotionMetricsResponse> getMetricsByRestaurant(Integer restaurantId) {
        return discountRepository.findPromotionMetricsByRestaurantId(restaurantId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void recordView(Integer promotionId) {
        int updated = discountRepository.incrementViewCount(promotionId);
        if (updated == 0) {
            throw new ResourceNotFoundException("Promotion not found.");
        }
    }

    private PromotionMetricsResponse toResponse(PromotionMetricsProjection p) {
        return PromotionMetricsResponse.builder()
                .promotionId(p.getPromotionId())
                .promotionName(p.getPromotionName())
                .restaurantId(p.getRestaurantId())
                .viewCount(p.getViewCount())
                .totalClaimed(p.getTotalClaimed())
                .totalRedeemed(p.getTotalRedeemed())
                .build();
    }
}
