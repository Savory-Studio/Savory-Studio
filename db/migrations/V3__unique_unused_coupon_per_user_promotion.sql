-- Guarantee at most one UNUSED coupon per (user, promotion) pair.
-- USED coupons are excluded so redemption history is preserved.
-- Concurrent inserts that pass the application-level check are rejected here.
CREATE UNIQUE INDEX IF NOT EXISTS uq_unused_coupon_per_user_promotion
    ON coupon (user_id, discount_id)
    WHERE coupon_status = 'UNUSED';
