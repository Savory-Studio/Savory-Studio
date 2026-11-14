package com.apg.promociones.apg_api.repository;

import com.apg.promociones.apg_api.model.Coupon;
import com.apg.promociones.apg_api.model.Discount;
import com.apg.promociones.apg_api.model.User;
import com.apg.promociones.apg_api.model.enums.CouponStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, Integer> {

    Optional<Coupon> findByCouponHash(String couponHash);

    /**
     * APG-65: loads the coupon with a PostgreSQL row lock ({@code SELECT ... FOR UPDATE}).
     * A concurrent redemption of the same coupon blocks until this transaction commits or rolls back,
     * and then reads the committed state, so RN-01 is evaluated against the winner's result.
     * Must be called inside a transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Coupon c WHERE c.id = :id")
    Optional<Coupon> findByIdForUpdate(@Param("id") Integer id);

    /**
     * APG-65: same as {@link #findByIdForUpdate(Integer)} but resolves the coupon by its QR hash.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Coupon c WHERE c.couponHash = :couponHash")
    Optional<Coupon> findByCouponHashForUpdate(@Param("couponHash") String couponHash);

    List<Coupon> findByUser_Id(Integer userId);

    // RN-04: count how many times this user has redeemed coupons for this discount
    @Query(value = "SELECT COUNT(*) FROM coupon c WHERE c.user_id = :#{#user.id} " +
            "AND c.discount_id = :#{#discount.id} AND c.coupon_status = " +
            "CAST(:#{#status.name()} AS coupon_status_enum)", nativeQuery = true)
    long countByUserAndDiscountAndStatus(User user, Discount discount, CouponStatus status);

    // RN-05: count total redemptions for this discount across all users
    @Query(value = "SELECT COUNT(*) FROM coupon c WHERE c.discount_id = " +
            ":#{#discount.id} AND c.coupon_status = CAST(:#{#status.name()} " +
            "AS coupon_status_enum)", nativeQuery = true)
    long countByDiscountAndStatus(Discount discount, CouponStatus status);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM coupon c WHERE c.user_id = :userId " +
            "AND c.discount_id = :discountId AND c.coupon_status = " +
            "CAST('UNUSED' AS coupon_status_enum))", nativeQuery = true)
    boolean existsUnusedByUserAndDiscount(Integer userId, Integer discountId);
}
