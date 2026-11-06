-- Add QR identifiers for existing coupons without replacing issued identifiers.
DO $$
BEGIN
    ALTER TABLE coupon ADD COLUMN IF NOT EXISTS coupon_hash VARCHAR(36);

    UPDATE coupon
    SET coupon_hash = gen_random_uuid()::text
    WHERE coupon_hash IS NULL;

    ALTER TABLE coupon ALTER COLUMN coupon_hash SET NOT NULL;

    -- The deployed database or snapshot can already enforce this under another name.
    IF NOT EXISTS (
        SELECT 1
        FROM pg_index AS idx
        JOIN pg_attribute AS col ON col.attrelid = idx.indrelid AND col.attnum = idx.indkey[0]
        WHERE idx.indrelid = 'coupon'::regclass AND col.attname = 'coupon_hash'
          AND idx.indisunique AND idx.indisvalid AND idx.indnkeyatts = 1 AND idx.indpred IS NULL
    ) THEN
        ALTER TABLE coupon ADD CONSTRAINT uq_coupon_hash UNIQUE (coupon_hash);
    END IF;
END $$;
