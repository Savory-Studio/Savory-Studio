package com.apg.promociones.apg_api.repository;

import com.apg.promociones.apg_api.model.Coupon;
import com.apg.promociones.apg_api.model.Discount;
import com.apg.promociones.apg_api.model.User;
import com.apg.promociones.apg_api.model.enums.CouponStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, Integer> {

    // RN-04: count how many times this user has redeemed coupons for this discount
    long countByUserAndDiscountAndStatus(User user, Discount discount, CouponStatus status);

    // RN-05: count total redemptions for this discount across all users
    long countByDiscountAndStatus(Discount discount, CouponStatus status);
}
