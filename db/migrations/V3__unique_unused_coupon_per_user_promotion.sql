-- Guarantee at most one UNUSED coupon per (user, promotion) pair.
-- USED coupons are excluded so redemption history is preserved.
-- Concurrent inserts that pass the application-level check are rejected here.

-- Remove pre-existing duplicates, keeping the earliest claim per pair.
DELETE FROM coupon
WHERE coupon_id IN (
    SELECT coupon_id FROM (
        SELECT coupon_id,
               ROW_NUMBER() OVER (
                   PARTITION BY user_id, discount_id
                   ORDER BY coupon_claim_timestamp ASC, coupon_id ASC
               ) AS rn
        FROM coupon
        WHERE coupon_status = 'UNUSED'
    ) numbered
    WHERE rn > 1
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_unused_coupon_per_user_promotion
    ON coupon (user_id, discount_id)
    WHERE coupon_status = 'UNUSED';
