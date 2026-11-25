package com.apg.promociones.apg_api.service;

import com.apg.promociones.apg_api.dto.PromotionMetricsProjection;
import com.apg.promociones.apg_api.dto.PromotionMetricsResponse;
import com.apg.promociones.apg_api.exception.ResourceNotFoundException;
import com.apg.promociones.apg_api.repository.DiscountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionMetricsServiceTest {

    @Mock
    private DiscountRepository discountRepository;

    @InjectMocks
    private PromotionMetricsService service;

    @Test
    void getAllMetricsReturnsMappedResponses() {
        PromotionMetricsProjection p1 = projection(1, "Promo A", 10, 50, 3L, 1L);
        PromotionMetricsProjection p2 = projection(2, "Promo B", 20, 0, 0L, 0L);
        when(discountRepository.findPromotionMetrics()).thenReturn(List.of(p1, p2));

        List<PromotionMetricsResponse> result = service.getAllMetrics();

        assertEquals(2, result.size());
        assertEquals(1, result.get(0).getPromotionId());
        assertEquals("Promo A", result.get(0).getPromotionName());
        assertEquals(10, result.get(0).getRestaurantId());
        assertEquals(50, result.get(0).getViewCount());
        assertEquals(3L, result.get(0).getTotalClaimed());
        assertEquals(1L, result.get(0).getTotalRedeemed());

        assertEquals(2, result.get(1).getPromotionId());
        assertEquals(0, result.get(1).getViewCount());
        assertEquals(0L, result.get(1).getTotalClaimed());
    }

    @Test
    void getAllMetricsReturnsEmptyListWhenNoPromotions() {
        when(discountRepository.findPromotionMetrics()).thenReturn(List.of());

        assertTrue(service.getAllMetrics().isEmpty());
    }

    @Test
    void getMetricsByRestaurantDelegatesToRepository() {
        PromotionMetricsProjection p = projection(5, "R1 Promo", 1, 10, 2L, 1L);
        when(discountRepository.findPromotionMetricsByRestaurantId(1)).thenReturn(List.of(p));

        List<PromotionMetricsResponse> result = service.getMetricsByRestaurant(1);

        assertEquals(1, result.size());
        assertEquals(5, result.get(0).getPromotionId());
        assertEquals(1, result.get(0).getRestaurantId());
        verify(discountRepository).findPromotionMetricsByRestaurantId(1);
    }

    @Test
    void getMetricsByRestaurantReturnsEmptyForUnknownRestaurant() {
        when(discountRepository.findPromotionMetricsByRestaurantId(999)).thenReturn(List.of());

        assertTrue(service.getMetricsByRestaurant(999).isEmpty());
    }

    @Test
    void recordViewIncrementsCounter() {
        when(discountRepository.incrementViewCount(1)).thenReturn(1);

        service.recordView(1);

        verify(discountRepository).incrementViewCount(1);
    }

    @Test
    void recordViewThrows404WhenPromotionDoesNotExist() {
        when(discountRepository.incrementViewCount(999)).thenReturn(0);

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> service.recordView(999));

        assertEquals("Promotion not found.", ex.getMessage());
    }

    @Test
    void getMetricsDoesNotCallIncrementViewCount() {
        when(discountRepository.findPromotionMetrics()).thenReturn(List.of());

        service.getAllMetrics();

        verify(discountRepository).findPromotionMetrics();
        verifyNoMoreInteractions(discountRepository);
    }

    private PromotionMetricsProjection projection(int id, String name, int restaurantId,
                                                   int views, long claimed, long redeemed) {
        return new PromotionMetricsProjection() {
            @Override public Integer getPromotionId() { return id; }
            @Override public String getPromotionName() { return name; }
            @Override public Integer getRestaurantId() { return restaurantId; }
            @Override public Integer getViewCount() { return views; }
            @Override public Long getTotalClaimed() { return claimed; }
            @Override public Long getTotalRedeemed() { return redeemed; }
        };
    }
}
