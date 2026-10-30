package com.apg.promociones.apg_api.service;

import com.apg.promociones.apg_api.dto.AssociatePromotionRequest;
import com.apg.promociones.apg_api.dto.AssociatePromotionResponse;
import com.apg.promociones.apg_api.exception.BusinessRuleException;
import com.apg.promociones.apg_api.exception.ResourceNotFoundException;
import com.apg.promociones.apg_api.model.Coupon;
import com.apg.promociones.apg_api.model.Discount;
import com.apg.promociones.apg_api.model.User;
import com.apg.promociones.apg_api.model.enums.CouponStatus;
import com.apg.promociones.apg_api.model.enums.DiscountStatus;
import com.apg.promociones.apg_api.repository.CouponRepository;
import com.apg.promociones.apg_api.repository.DiscountRepository;
import com.apg.promociones.apg_api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AssociatePromotionService {

    private final UserRepository userRepository;
    private final DiscountRepository discountRepository;
    private final CouponRepository couponRepository;

    /**
     * Associates an active promotion with the customer profile by creating an UNUSED coupon.
     * No coupon is persisted if the promotion does not exist or is not currently valid.
     */
    @Transactional
    public AssociatePromotionResponse associate(Integer userId, AssociatePromotionRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        Discount discount = discountRepository.findById(request.getPromotionId())
                .orElseThrow(() -> new ResourceNotFoundException("Promotion not found."));

        validatePromotionIsCurrent(discount);

        if (couponRepository.existsUnusedByUserAndDiscount(user.getId(), discount.getId())) {
            throw new BusinessRuleException("This promotion is already associated with the customer profile.");
        }

        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = Coupon.builder()
                .user(user)
                .discount(discount)
                .status(CouponStatus.UNUSED)
                .claimTimestamp(now)
                .build();
        coupon = couponRepository.save(coupon);

        return AssociatePromotionResponse.builder()
                .couponId(coupon.getId())
                .userId(user.getId())
                .promotionId(discount.getId())
                .status(CouponStatus.UNUSED.name())
                .claimTimestamp(now)
                .message("Promotion associated with the customer profile.")
                .build();
    }

    private void validatePromotionIsCurrent(Discount discount) {
        if (discount.getStatus() != DiscountStatus.Active) {
            throw new BusinessRuleException("This promotion is no longer available.");
        }

        LocalDateTime now = LocalDateTime.now();

        if (discount.getStartTimestamp() != null && discount.getStartTimestamp().isAfter(now)) {
            throw new BusinessRuleException("This promotion is not active yet.");
        }

        if (discount.getEndTimestamp() != null && discount.getEndTimestamp().isBefore(now)) {
            throw new BusinessRuleException("This promotion has expired.");
        }
    }
}
