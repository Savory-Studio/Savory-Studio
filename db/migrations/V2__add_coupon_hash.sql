-- =============================================================
-- V2: Add coupon_hash column for QR-based redemption
-- Covers: APG-35
-- Idempotent: safe to run on databases with or without the column
-- =============================================================

-- Add column if it does not exist
DO $$ BEGIN
    ALTER TABLE coupon ADD COLUMN coupon_hash VARCHAR(36);
EXCEPTION WHEN duplicate_column THEN NULL;
END $$;

-- Backfill existing rows that have no hash
UPDATE coupon
SET    coupon_hash = gen_random_uuid()::text
WHERE  coupon_hash IS NULL;

-- Set NOT NULL after backfill
ALTER TABLE coupon ALTER COLUMN coupon_hash SET NOT NULL;

-- Add unique constraint if it does not exist
DO $$ BEGIN
    ALTER TABLE coupon ADD CONSTRAINT uq_coupon_hash UNIQUE (coupon_hash);
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;
