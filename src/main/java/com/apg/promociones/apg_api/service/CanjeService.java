package com.apg.promociones.apg_api.service;

import com.apg.promociones.apg_api.dto.CanjeRequest;
import com.apg.promociones.apg_api.dto.CanjeResponse;
import com.apg.promociones.apg_api.exception.BadRequestException;
import com.apg.promociones.apg_api.exception.BusinessRuleException;
import com.apg.promociones.apg_api.exception.ResourceNotFoundException;
import com.apg.promociones.apg_api.exception.UnauthorizedException;
import com.apg.promociones.apg_api.model.Coupon;
import com.apg.promociones.apg_api.model.Discount;
import com.apg.promociones.apg_api.model.enums.CouponStatus;
import com.apg.promociones.apg_api.model.enums.DiscountStatus;
import com.apg.promociones.apg_api.repository.CouponRepository;
import com.apg.promociones.apg_api.repository.DiscountRepository;
import com.apg.promociones.apg_api.repository.RestaurantStaffRepository;
import com.apg.promociones.apg_api.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class CanjeService {

    private final CouponRepository couponRepository;
    private final DiscountRepository discountRepository;
    private final RestaurantStaffRepository restaurantStaffRepository;

    @Autowired
    public CanjeService(
            CouponRepository couponRepository,
            DiscountRepository discountRepository,
            RestaurantStaffRepository restaurantStaffRepository) {
        this.couponRepository = couponRepository;
        this.discountRepository = discountRepository;
        this.restaurantStaffRepository = restaurantStaffRepository;
    }

    public CanjeService(CouponRepository couponRepository, DiscountRepository discountRepository) {
        this(couponRepository, discountRepository, null);
    }

    /**
     * Validates and processes a coupon redemption.
     * Applies business rules RN-01 through RN-05 in sequence.
     * On any rule violation, throws an exception — no DB state is modified.
     * <p>
     * Concurrency strategy (APG-65): {@code @Transactional} alone does not prevent two simultaneous
     * requests from reading the same state, so the transaction takes PostgreSQL row locks
     * ({@code SELECT ... FOR UPDATE}) before evaluating any rule:
     * <ol>
     *   <li>the coupon row, so concurrent redemptions of the same coupon are serialized (RN-01);</li>
     *   <li>the discount row, so redemptions of different coupons of the same promotion are serialized
     *       and the usage counts of RN-04 / RN-05 are read after the previous redemption committed.</li>
     * </ol>
     * Locks are always taken in the same order (coupon, then discount) and are released on commit or
     * rollback, so a rejected request leaves the data untouched and no deadlock cycle can form.
     */
    @Transactional
    public CanjeResponse redeem(CanjeRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Integer authUserId = SecurityUtils.getCurrentUserId().orElse(null);
        if (authentication != null && authentication.isAuthenticated() && authUserId == null) {
            throw new UnauthorizedException("Could not identify the authenticated user.");
        }

        // --- Validate that at least one identifier is present ---
        String normalizedHash = (request.getCouponHash() == null || request.getCouponHash().isBlank())
                ? null
                : request.getCouponHash().trim();
        if (normalizedHash == null && request.getCouponId() == null) {
            throw new BadRequestException("Debe proporcionar couponId o couponHash.");
        }

        // --- Fetch and lock the coupon by hash (priority) or by ID ---
        Coupon coupon;
        if (normalizedHash != null) {
            coupon = couponRepository.findByCouponHashForUpdate(normalizedHash)
                    .orElseThrow(() -> new ResourceNotFoundException("Cupón no encontrado."));
        } else {
            coupon = couponRepository.findByIdForUpdate(request.getCouponId())
                    .orElseThrow(() -> new ResourceNotFoundException("Cupón no encontrado."));
        }

        // --- Lock the discount row (getId() on the lazy reference does not load the discount) ---
        Discount discount = discountRepository.findByIdForUpdate(coupon.getDiscount().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Promoción no encontrada."));

        // --- Contextual authorization (CUP-04): allow restaurant owner or linked restaurant_staff ---
        if (authUserId != null && discount.getRestaurant() != null) {
            Integer restaurantId = discount.getRestaurant().getId();
            Integer ownerId = discount.getRestaurant().getOwner() != null
                    ? discount.getRestaurant().getOwner().getId()
                    : null;
            boolean isOwner = ownerId != null && ownerId.equals(authUserId);
            boolean isAuthorizedStaff = restaurantId != null
                    && restaurantStaffRepository != null
                    && restaurantStaffRepository.existsByUserIdAndRestaurantId(authUserId, restaurantId);
            if (ownerId != null && !isOwner && !isAuthorizedStaff) {
                throw new AccessDeniedException("Usuario no tiene permiso para canjear cupones de este restaurante.");
            }
        }

        // --- RN-01: coupon already used ---
        if (coupon.getStatus() == CouponStatus.USED) {
            throw new BusinessRuleException("Este cupón ya fue utilizado.");
        }

        // --- RN-02: discount inactive ---
        if (discount.getStatus() == DiscountStatus.Suspended) {
            throw new BusinessRuleException("Esta promoción ya no está disponible.");
        }
        if (discount.getStatus() == DiscountStatus.Finished) {
            if (discount.getMaxUses() != null) {
                long usedCount = couponRepository.countByDiscountAndStatus(discount, CouponStatus.USED);
                if (discount.isExhaustedWithUsedCount(usedCount)) {
                    throw new BusinessRuleException("Esta promoción se ha agotado.");
                }
            }
            throw new BusinessRuleException("Esta promoción ya no está disponible.");
        }

        // --- RN-03: discount expired ---
        LocalDateTime now = LocalDateTime.now();
        if (discount.isExpiredAt(now)) {
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
        long totalRedemptionsBefore = 0L;
        if (discount.getMaxUses() != null) {
            totalRedemptionsBefore = couponRepository.countByDiscountAndStatus(
                    discount, CouponStatus.USED);
            if (discount.isExhaustedWithUsedCount(totalRedemptionsBefore)) {
                throw new BusinessRuleException("Esta promoción se ha agotado.");
            }
        }

        // --- All rules pass — execute redemption (flushed while both row locks are still held) ---
        coupon.setStatus(CouponStatus.USED);
        coupon.setRedemptionTimestamp(now);
        couponRepository.saveAndFlush(coupon);

        // --- APG-66: close promotion immediately when the final available use is redeemed ---
        if (discount.isExhaustedWithUsedCount(totalRedemptionsBefore + 1) && discount.finishIfActive()) {
            discountRepository.saveAndFlush(discount);
        }

        return CanjeResponse.builder()
                .couponId(coupon.getId())
                .couponHash(coupon.getCouponHash())
                .status("USED")
                .redemptionTimestamp(now)
                .message("Cupón canjeado exitosamente.")
                .build();
    }
}
