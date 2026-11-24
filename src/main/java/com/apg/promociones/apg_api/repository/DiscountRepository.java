package com.apg.promociones.apg_api.repository;

import com.apg.promociones.apg_api.dto.PromotionMetricsProjection;
import com.apg.promociones.apg_api.model.Discount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DiscountRepository extends JpaRepository<Discount, Integer> {

    /**
     * APG-65: loads the promotion with a PostgreSQL row lock ({@code SELECT ... FOR UPDATE}).
     * Redemptions of different coupons of the same promotion are serialized on this row, so the
     * RN-04 / RN-05 usage counts are always evaluated after the previous redemption has committed.
     * Must be called inside a transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Discount d WHERE d.id = :id")
    Optional<Discount> findByIdForUpdate(@Param("id") Integer id);

    @Query(value = """
            SELECT d.*
            FROM discounts d
            LEFT JOIN (
                SELECT discount_id, COUNT(*) AS used_count
                FROM coupon
                WHERE coupon_status = CAST('USED' AS coupon_status_enum)
                GROUP BY discount_id
            ) usage ON usage.discount_id = d.discount_id
            WHERE d.discount_status = CAST('Active' AS discount_status_enum)
              AND (d.discount_start_timestamp IS NULL OR d.discount_start_timestamp <= CURRENT_TIMESTAMP)
              AND (d.discount_end_timestamp IS NULL OR d.discount_end_timestamp >= CURRENT_TIMESTAMP)
              AND (d.discount_max_uses IS NULL OR COALESCE(usage.used_count, 0) < d.discount_max_uses)
            ORDER BY d.discount_id
            """, nativeQuery = true)
    List<Discount> findActivePromotions();

    /**
     * APG-66: transitions {@code Active} promotions past {@code discount_end_timestamp} to {@code Finished}.
     * {@code Suspended} promotions are intentionally left untouched.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE discounts
            SET discount_status = CAST('Finished' AS discount_status_enum)
            WHERE discount_status = CAST('Active' AS discount_status_enum)
              AND discount_end_timestamp IS NOT NULL
              AND discount_end_timestamp < CURRENT_TIMESTAMP
            """, nativeQuery = true)
    int finishExpiredPromotions();

    /**
     * APG-66: transitions {@code Active} promotions whose {@code USED} coupon count reached {@code discount_max_uses}
     * to {@code Finished}. {@code Suspended} promotions and {@code coupon} history remain untouched.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE discounts d
            SET discount_status = CAST('Finished' AS discount_status_enum)
            WHERE d.discount_status = CAST('Active' AS discount_status_enum)
              AND d.discount_max_uses IS NOT NULL
              AND (
                  SELECT COUNT(*)
                  FROM coupon c
                  WHERE c.discount_id = d.discount_id
                    AND c.coupon_status = CAST('USED' AS coupon_status_enum)
              ) >= d.discount_max_uses
            """, nativeQuery = true)
    int finishExhaustedPromotions();

    @Query(value = """
        SELECT EXISTS (
            SELECT 1
            FROM discounts
            WHERE discount_restaurant_id = :restaurantId
              AND UPPER(discount_name) = UPPER(:name)
              AND discount_status = CAST(:status AS discount_status_enum)
        )
        """, nativeQuery = true)
    boolean existsActiveByRestaurantAndName(
            @Param("restaurantId") Integer restaurantId,
            @Param("name") String name,
            @Param("status") String status
    );

    @Query(value = """
            SELECT d.discount_id        AS promotionId,
                   d.discount_name       AS promotionName,
                   d.discount_restaurant_id AS restaurantId,
                   d.discount_view_amount AS viewCount,
                   COUNT(c.coupon_id)    AS totalClaimed,
                   COUNT(c.coupon_id) FILTER (WHERE c.coupon_status = CAST('USED' AS coupon_status_enum))
                                         AS totalRedeemed
            FROM discounts d
            LEFT JOIN coupon c ON c.discount_id = d.discount_id
            GROUP BY d.discount_id, d.discount_name, d.discount_restaurant_id, d.discount_view_amount
            ORDER BY d.discount_id
            """, nativeQuery = true)
    List<PromotionMetricsProjection> findPromotionMetrics();

    @Query(value = """
            SELECT d.discount_id        AS promotionId,
                   d.discount_name       AS promotionName,
                   d.discount_restaurant_id AS restaurantId,
                   d.discount_view_amount AS viewCount,
                   COUNT(c.coupon_id)    AS totalClaimed,
                   COUNT(c.coupon_id) FILTER (WHERE c.coupon_status = CAST('USED' AS coupon_status_enum))
                                         AS totalRedeemed
            FROM discounts d
            LEFT JOIN coupon c ON c.discount_id = d.discount_id
            WHERE d.discount_restaurant_id = :restaurantId
            GROUP BY d.discount_id, d.discount_name, d.discount_restaurant_id, d.discount_view_amount
            ORDER BY d.discount_id
            """, nativeQuery = true)
    List<PromotionMetricsProjection> findPromotionMetricsByRestaurantId(
            @Param("restaurantId") Integer restaurantId);
}
