package com.apg.promociones.apg_api.repository;

import com.apg.promociones.apg_api.model.Discount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DiscountRepository extends JpaRepository<Discount, Integer> {

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
}
