package com.apg.promociones.apg_api.service;

import com.apg.promociones.apg_api.model.Discount;
import com.apg.promociones.apg_api.model.enums.DiscountStatus;
import com.apg.promociones.apg_api.repository.DiscountRepository;
import com.apg.promociones.apg_api.service.PromotionLifecycleService.PromotionCloseSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionLifecycleServiceTest {

    @Mock
    private DiscountRepository discountRepository;

    @Test
    void discountDomainHelpersDetectExpirationExhaustionAndPreserveSuspended() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 23, 12, 0);

        Discount expired = Discount.builder()
                .status(DiscountStatus.Active)
                .endTimestamp(now.minusSeconds(1))
                .build();
        assertTrue(expired.isExpiredAt(now));
        assertFalse(expired.isExpiredAt(now.minusSeconds(1)));

        Discount unlimited = Discount.builder().status(DiscountStatus.Active).build();
        assertFalse(unlimited.isExpiredAt(now));
        assertFalse(unlimited.isExhaustedWithUsedCount(99));

        Discount limited = Discount.builder().status(DiscountStatus.Active).maxUses(3).build();
        assertFalse(limited.isExhaustedWithUsedCount(2));
        assertTrue(limited.isExhaustedWithUsedCount(3));
        assertTrue(limited.isExhaustedWithUsedCount(4));

        assertTrue(limited.finishIfActive());
        assertEquals(DiscountStatus.Finished, limited.getStatus());
        assertFalse(limited.finishIfActive());

        Discount suspended = Discount.builder().status(DiscountStatus.Suspended).build();
        assertFalse(suspended.finishIfActive());
        assertEquals(DiscountStatus.Suspended, suspended.getStatus());
    }

    @Test
    void closeExpiredAndExhaustedPromotionsRunsBothRepositoryUpdatesAndReturnsSummary() {
        when(discountRepository.finishExpiredPromotions()).thenReturn(2);
        when(discountRepository.finishExhaustedPromotions()).thenReturn(1);

        PromotionLifecycleService service = new PromotionLifecycleService(discountRepository);
        PromotionCloseSummary summary = service.closeExpiredAndExhaustedPromotions();

        assertEquals(2, summary.expiredClosed());
        assertEquals(1, summary.exhaustedClosed());
        assertEquals(3, summary.totalClosed());
        verify(discountRepository).finishExpiredPromotions();
        verify(discountRepository).finishExhaustedPromotions();
    }

    @Test
    void schedulerDelegatesToLifecycleService() {
        when(discountRepository.finishExpiredPromotions()).thenReturn(0);
        when(discountRepository.finishExhaustedPromotions()).thenReturn(0);

        PromotionLifecycleService service = new PromotionLifecycleService(discountRepository);
        PromotionLifecycleScheduler scheduler = new PromotionLifecycleScheduler(service);

        scheduler.closeExpiredAndExhaustedPromotions();

        verify(discountRepository).finishExpiredPromotions();
        verify(discountRepository).finishExhaustedPromotions();
    }
}
