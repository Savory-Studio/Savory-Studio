package com.apg.promociones.apg_api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically reconciles expired and usage-exhausted promotions even when no HTTP requests arrive.
 */
@Component
@RequiredArgsConstructor
public class PromotionLifecycleScheduler {

    private final PromotionLifecycleService promotionLifecycleService;

    @Scheduled(fixedDelayString = "${app.promotion-lifecycle.close-interval-ms:60000}")
    public void closeExpiredAndExhaustedPromotions() {
        promotionLifecycleService.closeExpiredAndExhaustedPromotions();
    }
}
