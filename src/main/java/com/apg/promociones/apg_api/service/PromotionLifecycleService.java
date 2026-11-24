package com.apg.promociones.apg_api.service;

import com.apg.promociones.apg_api.repository.DiscountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transitions {@code Active} promotions to {@code Finished} when their expiration timestamp passes
 * or their {@code USED} redemption limit is reached, leaving {@code Suspended} promotions and
 * {@code coupon} history untouched.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PromotionLifecycleService {

    private final DiscountRepository discountRepository;

    /**
     * Summary of promotions transitioned from {@code Active} to {@code Finished} in one pass.
     */
    public record PromotionCloseSummary(int expiredClosed, int exhaustedClosed) {
        public int totalClosed() {
            return expiredClosed + exhaustedClosed;
        }
    }

    /**
     * Closes all currently expired or usage-exhausted {@code Active} promotions atomically.
     */
    @Transactional
    public PromotionCloseSummary closeExpiredAndExhaustedPromotions() {
        int expiredClosed = discountRepository.finishExpiredPromotions();
        int exhaustedClosed = discountRepository.finishExhaustedPromotions();

        if (expiredClosed > 0 || exhaustedClosed > 0) {
            log.info("APG-66 promotion closure updated status to Finished (expired={}, exhausted={})",
                    expiredClosed, exhaustedClosed);
        }

        return new PromotionCloseSummary(expiredClosed, exhaustedClosed);
    }
}
