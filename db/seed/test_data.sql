-- =============================================================
-- Seed: test data for the 4 tables
-- Idempotent: uses ON CONFLICT DO NOTHING
-- =============================================================

-- Only seed isolated test databases. Keep the fixed IDs and their sequences in sync.
BEGIN;
LOCK TABLE users, restaurants, discounts, coupon IN SHARE ROW EXCLUSIVE MODE;

-- Users (passwords are BCrypt hashes of 'password123')
INSERT INTO users (user_id, user_first_name, user_second_name, user_last_name, user_birthday, user_email, user_password_hash)
VALUES
    (1, 'Carlos', 'Andrés', 'Martínez', '1990-03-15', 'carlos@example.com',
     '$2a$10$oGoA4Q3jq9MBf.25NR5oMO5lF4t9h69UbdMVNLUnCaH5FIou3uGt2'),
    (2, 'María', NULL, 'López', '1995-07-22', 'maria@example.com',
     '$2a$10$oGoA4Q3jq9MBf.25NR5oMO5lF4t9h69UbdMVNLUnCaH5FIou3uGt2'),
    (3, 'Emmanuel', 'Alejandro', 'Peñuela', '2000-05-15', 'emmanuel@example.com',
     '$2a$10$oGoA4Q3jq9MBf.25NR5oMO5lF4t9h69UbdMVNLUnCaH5FIou3uGt2')
ON CONFLICT (user_id) DO NOTHING;

-- Restaurants
INSERT INTO restaurants (restaurant_id, restaurant_name, restaurant_owner_id, restaurant_description, restaurant_full_address)
VALUES
    (1, 'La Cazuela Criolla', 1, 'Restaurante de comida típica colombiana', 'Calle 45 #12-30, Bucaramanga'),
    (2, 'Sushi Express', 2, 'Sushi y comida japonesa a domicilio', 'Carrera 33 #48-15, Bucaramanga')
ON CONFLICT (restaurant_id) DO NOTHING;

-- Discounts: one of each type
INSERT INTO discounts (discount_id, discount_restaurant_id, discount_name, discount_status, discount_applicable_products,
                       discount_type, discount_amount, discount_customer_takes_amount, discount_customer_pays_amount,
                       discount_start_timestamp, discount_end_timestamp, discount_max_uses, discount_max_use_amount_per_user)
VALUES
    -- PERCENTAGE type
    (1, 1, 'Almuerzo 20% off', 'Active', 'Todos los platos del menú ejecutivo',
     'PERCENTAGE', 20.00, NULL, NULL,
     '2026-10-01 08:00:00', '2026-12-31 23:59:00', 100, 2),
    -- FIXED_VALUE type
    (2, 1, '$5.000 de descuento', 'Active', 'Platos principales',
     'FIXED_VALUE', 5000.00, NULL, NULL,
     '2026-10-01 08:00:00', NULL, 50, 1),
    -- IXJ type (2x1)
    (3, 2, '2x1 en rolls', 'Active', 'Todos los rolls clásicos',
     'IXJ', NULL, 2, 1,
     '2026-10-01 08:00:00', '2026-12-31 23:59:00', NULL, 3),
    -- Finished promotion (for testing rejection)
    (4, 2, 'Promo vencida', 'Finished', 'Bebidas',
     'PERCENTAGE', 10.00, NULL, NULL,
     '2026-01-01 08:00:00', '2026-09-30 23:59:00', 50, 1)
ON CONFLICT (discount_id) DO NOTHING;

-- Coupons: mix of UNUSED and USED
INSERT INTO coupon (coupon_id, user_id, discount_id, coupon_status, coupon_claim_timestamp, coupon_redemption_timestamp, coupon_hash)
VALUES
    -- UNUSED coupon
    (1, 3, 1, 'UNUSED', '2026-10-28 10:00:00', NULL,
     'a1b2c3d4-e5f6-7890-abcd-000000000001'),
    -- USED coupon
    (2, 3, 2, 'USED', '2026-10-25 14:00:00', '2026-10-26 12:30:00',
     'a1b2c3d4-e5f6-7890-abcd-000000000002'),
    -- UNUSED coupon from another user
    (3, 1, 3, 'UNUSED', '2026-10-29 09:00:00', NULL,
     'a1b2c3d4-e5f6-7890-abcd-000000000003')
ON CONFLICT (coupon_id) DO NOTHING;

-- Explicit identity values do not advance the sequence. Never rewind a sequence on retry.
SELECT setval(sequence_name, highest_id)
FROM (VALUES
    (pg_get_serial_sequence('users', 'user_id')::regclass, (SELECT max(user_id) FROM users)),
    (pg_get_serial_sequence('restaurants', 'restaurant_id')::regclass, (SELECT max(restaurant_id) FROM restaurants)),
    (pg_get_serial_sequence('discounts', 'discount_id')::regclass, (SELECT max(discount_id) FROM discounts)),
    (pg_get_serial_sequence('coupon', 'coupon_id')::regclass, (SELECT max(coupon_id) FROM coupon))
) AS seeded_sequences(sequence_name, highest_id)
WHERE COALESCE(pg_sequence_last_value(sequence_name), 0) < highest_id;

COMMIT;
