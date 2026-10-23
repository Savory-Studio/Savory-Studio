package com.apg.promociones.apg_api.service;

import com.apg.promociones.apg_api.dto.CanjeRequest;
import com.apg.promociones.apg_api.dto.CanjeResponse;
import com.apg.promociones.apg_api.exception.BusinessRuleException;
import com.apg.promociones.apg_api.exception.ResourceNotFoundException;
import com.apg.promociones.apg_api.model.Coupon;
import com.apg.promociones.apg_api.model.Discount;
import com.apg.promociones.apg_api.model.enums.CouponStatus;
import com.apg.promociones.apg_api.model.enums.DiscountStatus;
import com.apg.promociones.apg_api.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CanjeService {

    private final CouponRepository couponRepository;

    /**
     * Validates and processes a coupon redemption.
     * Applies business rules RN-01 through RN-05 in sequence.
     * On any rule violation, throws an exception — no DB state is modified.
     */
    @Transactional
    public CanjeResponse redeem(CanjeRequest request) {

        // --- Fetch coupon or 404 ---
        Coupon coupon = couponRepository.findById(request.getCouponId())
                .orElseThrow(() -> new ResourceNotFoundException("Cupón no encontrado."));

        Discount discount = coupon.getDiscount();

        // --- RN-01: coupon already used ---
        if (coupon.getStatus() == CouponStatus.USED) {
            throw new BusinessRuleException("Este cupón ya fue utilizado.");
        }

        // --- RN-02: discount inactive ---
        if (discount.getStatus() == DiscountStatus.Finished
                || discount.getStatus() == DiscountStatus.Suspended) {
            throw new BusinessRuleException("Esta promoción ya no está disponible.");
        }

        // --- RN-03: discount expired ---
        if (discount.getEndTimestamp() != null
                && discount.getEndTimestamp().isBefore(LocalDateTime.now())) {
            throw new BusinessRuleException("Esta promoción ha vencido.");
        }

        // --- RN-04: user limit per discount ---
        if (discount.getMaxUseAmountPerUser() != null) {
            long userRedemptions = couponRepository.countByUserAndDiscountAndStatus(
                    coupon.getUser(), discount, CouponStatus.USED);
            if (userRedemptions >= discount.getMaxUseAmountPerUser()) {
                throw new BusinessRuleException(
                        "Has alcanzado el límite de usos de esta promoción.");
            }
        }

        // --- RN-05: global limit ---
        if (discount.getMaxUses() != null) {
            long totalRedemptions = couponRepository.countByDiscountAndStatus(
                    discount, CouponStatus.USED);
            if (totalRedemptions >= discount.getMaxUses()) {
                throw new BusinessRuleException("Esta promoción se ha agotado.");
            }
        }

        // --- All rules pass — execute redemption ---
        LocalDateTime now = LocalDateTime.now();
        coupon.setStatus(CouponStatus.USED);
        coupon.setRedemptionTimestamp(now);
        couponRepository.save(coupon);

        return CanjeResponse.builder()
                .couponId(coupon.getId())
                .status("USED")
                .redemptionTimestamp(now)
                .message("Cupón canjeado exitosamente.")
                .build();
    }
}
