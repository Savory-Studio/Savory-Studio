BEGIN;

INSERT INTO users (user_first_name, user_second_name, user_last_name, user_birthday, user_email, user_password_hash)
VALUES ('Lucía', NULL, 'Rojas', DATE '2001-06-12', 'lucia.cliente@savorystudio.test', '$2y$10$8nXyBRP0esXEomEF94hTgeWDspbKkHlHyLJ5/fQ4p3Ofhp6TQIwQy')
ON CONFLICT (user_email) DO UPDATE
SET user_password_hash = EXCLUDED.user_password_hash;

INSERT INTO users (user_first_name, user_second_name, user_last_name, user_birthday, user_email, user_password_hash)
VALUES ('Santiago', NULL, 'Mejía', DATE '1999-10-08', 'santiago.cliente@savorystudio.test', '$2y$10$IWMFQxb4wZwN5NmdMMrBW.4Q1eJGj9kGvDy0Ost3zeTQsCuvIb.B2')
ON CONFLICT (user_email) DO UPDATE
SET user_password_hash = EXCLUDED.user_password_hash;

INSERT INTO users (user_first_name, user_second_name, user_last_name, user_birthday, user_email, user_password_hash)
VALUES ('Valentina', NULL, 'Castro', DATE '2002-04-25', 'valentina.cliente@savorystudio.test', '$2y$10$IkNMlSZNrtPeIs2ujZ/TsulNcaoO7OwJyuugPcBE2kZoW/SvhCBWC')
ON CONFLICT (user_email) DO UPDATE
SET user_password_hash = EXCLUDED.user_password_hash;

INSERT INTO users (user_first_name, user_second_name, user_last_name, user_birthday, user_email, user_password_hash)
VALUES ('María', NULL, 'López', DATE '1987-03-15', 'maria.propietaria@savorystudio.test', '$2y$10$66HQCiV4BRMSc1F7oY53u.0BjHYulU93KFY3fsTmEpjJF1OD90LXq')
ON CONFLICT (user_email) DO UPDATE
SET user_password_hash = EXCLUDED.user_password_hash;

INSERT INTO users (user_first_name, user_second_name, user_last_name, user_birthday, user_email, user_password_hash)
VALUES ('Andrés', NULL, 'Torres', DATE '1984-11-20', 'andres.propietario@savorystudio.test', '$2y$10$a/Y0jtssR37sts0qj1E0COUxDu.Iex0vdjBuPvIS9wznI5qefQc4y')
ON CONFLICT (user_email) DO UPDATE
SET user_password_hash = EXCLUDED.user_password_hash;

INSERT INTO restaurants (restaurant_name, restaurant_owner_id, restaurant_description, restaurant_full_address)
SELECT 'Mock - La Mesa del Centro', u.user_id, 'Restaurante ficticio para pruebas de almuerzos y postres.', 'Dirección ficticia 101, Manizales'
FROM users u
WHERE u.user_email = 'maria.propietaria@savorystudio.test'
  AND NOT EXISTS (
      SELECT 1 FROM restaurants r
      WHERE r.restaurant_name = 'Mock - La Mesa del Centro'
        AND r.restaurant_owner_id = u.user_id
  );

INSERT INTO restaurants (restaurant_name, restaurant_owner_id, restaurant_description, restaurant_full_address)
SELECT 'Mock - Patio del Sabor', u.user_id, 'Restaurante ficticio para pruebas de bebidas y cenas.', 'Dirección ficticia 202, Manizales'
FROM users u
WHERE u.user_email = 'andres.propietario@savorystudio.test'
  AND NOT EXISTS (
      SELECT 1 FROM restaurants r
      WHERE r.restaurant_name = 'Mock - Patio del Sabor'
        AND r.restaurant_owner_id = u.user_id
  );

INSERT INTO discounts (
    discount_restaurant_id, discount_view_amount, discount_name, discount_status,
    discount_applicable_products, discount_type, discount_amount,
    discount_customer_takes_amount, discount_customer_pays_amount,
    discount_start_timestamp, discount_end_timestamp,
    discount_max_uses, discount_max_use_amount_per_user,
    discount_additional_use_conditions
)
SELECT r.restaurant_id, 32, 'Mock - Almuerzo 20%', 'Active',
       'Platos de almuerzo', 'PERCENTAGE', 20.00, NULL, NULL,
       CURRENT_TIMESTAMP - INTERVAL '7 days', CURRENT_TIMESTAMP + INTERVAL '14 days', NULL, 2, 'No acumulable con otras promociones.'
FROM restaurants r
WHERE r.restaurant_name = 'Mock - La Mesa del Centro'
  AND NOT EXISTS (
      SELECT 1 FROM discounts d
      WHERE d.discount_restaurant_id = r.restaurant_id
        AND d.discount_name = 'Mock - Almuerzo 20%'
  );

INSERT INTO discounts (
    discount_restaurant_id, discount_view_amount, discount_name, discount_status,
    discount_applicable_products, discount_type, discount_amount,
    discount_customer_takes_amount, discount_customer_pays_amount,
    discount_start_timestamp, discount_end_timestamp,
    discount_max_uses, discount_max_use_amount_per_user,
    discount_additional_use_conditions
)
SELECT r.restaurant_id, 24, 'Mock - Bebidas 2x1', 'Active',
       'Bebidas seleccionadas', 'IXJ', NULL, 2, 1,
       CURRENT_TIMESTAMP - INTERVAL '7 days', NULL, 5, 2, 'Aplica a dos bebidas del mismo precio.'
FROM restaurants r
WHERE r.restaurant_name = 'Mock - Patio del Sabor'
  AND NOT EXISTS (
      SELECT 1 FROM discounts d
      WHERE d.discount_restaurant_id = r.restaurant_id
        AND d.discount_name = 'Mock - Bebidas 2x1'
  );

INSERT INTO discounts (
    discount_restaurant_id, discount_view_amount, discount_name, discount_status,
    discount_applicable_products, discount_type, discount_amount,
    discount_customer_takes_amount, discount_customer_pays_amount,
    discount_start_timestamp, discount_end_timestamp,
    discount_max_uses, discount_max_use_amount_per_user,
    discount_additional_use_conditions
)
SELECT r.restaurant_id, 41, 'Mock - Descuento de 10000', 'Finished',
       'Platos principales', 'FIXED_VALUE', 10000.00, NULL, NULL,
       CURRENT_TIMESTAMP - INTERVAL '14 days', CURRENT_TIMESTAMP - INTERVAL '2 days', NULL, 1, 'Promoción vencida; se conserva para pruebas.'
FROM restaurants r
WHERE r.restaurant_name = 'Mock - La Mesa del Centro'
  AND NOT EXISTS (
      SELECT 1 FROM discounts d
      WHERE d.discount_restaurant_id = r.restaurant_id
        AND d.discount_name = 'Mock - Descuento de 10000'
  );

INSERT INTO discounts (
    discount_restaurant_id, discount_view_amount, discount_name, discount_status,
    discount_applicable_products, discount_type, discount_amount,
    discount_customer_takes_amount, discount_customer_pays_amount,
    discount_start_timestamp, discount_end_timestamp,
    discount_max_uses, discount_max_use_amount_per_user,
    discount_additional_use_conditions
)
SELECT r.restaurant_id, 18, 'Mock - Postres 3x2', 'Finished',
       'Postres seleccionados', 'IXJ', NULL, 3, 2,
       CURRENT_TIMESTAMP - INTERVAL '7 days', NULL, 2, 1, 'Promoción agotada al alcanzar dos canjes efectivos.'
FROM restaurants r
WHERE r.restaurant_name = 'Mock - La Mesa del Centro'
  AND NOT EXISTS (
      SELECT 1 FROM discounts d
      WHERE d.discount_restaurant_id = r.restaurant_id
        AND d.discount_name = 'Mock - Postres 3x2'
  );

INSERT INTO discounts (
    discount_restaurant_id, discount_view_amount, discount_name, discount_status,
    discount_applicable_products, discount_type, discount_amount,
    discount_customer_takes_amount, discount_customer_pays_amount,
    discount_start_timestamp, discount_end_timestamp,
    discount_max_uses, discount_max_use_amount_per_user,
    discount_additional_use_conditions
)
SELECT r.restaurant_id, 9, 'Mock - Cena 15%', 'Suspended',
       'Platos de cena', 'PERCENTAGE', 15.00, NULL, NULL,
       CURRENT_TIMESTAMP - INTERVAL '5 days', CURRENT_TIMESTAMP + INTERVAL '30 days', NULL, 1, 'Promoción suspendida para pruebas de validación.'
FROM restaurants r
WHERE r.restaurant_name = 'Mock - Patio del Sabor'
  AND NOT EXISTS (
      SELECT 1 FROM discounts d
      WHERE d.discount_restaurant_id = r.restaurant_id
        AND d.discount_name = 'Mock - Cena 15%'
  );

INSERT INTO coupon (user_id, discount_id, coupon_status, coupon_claim_timestamp, coupon_redemption_timestamp)
SELECT u.user_id, d.discount_id, 'USED', CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '1 day'
FROM users u
JOIN discounts d ON d.discount_name = 'Mock - Almuerzo 20%'
WHERE u.user_email = 'lucia.cliente@savorystudio.test'
  AND NOT EXISTS (
      SELECT 1 FROM coupon c
      WHERE c.user_id = u.user_id
        AND c.discount_id = d.discount_id
  );

INSERT INTO coupon (user_id, discount_id, coupon_status, coupon_claim_timestamp, coupon_redemption_timestamp)
SELECT u.user_id, d.discount_id, 'UNUSED', CURRENT_TIMESTAMP - INTERVAL '1 hour', NULL
FROM users u
JOIN discounts d ON d.discount_name = 'Mock - Almuerzo 20%'
WHERE u.user_email = 'santiago.cliente@savorystudio.test'
  AND NOT EXISTS (
      SELECT 1 FROM coupon c
      WHERE c.user_id = u.user_id
        AND c.discount_id = d.discount_id
  );

INSERT INTO coupon (user_id, discount_id, coupon_status, coupon_claim_timestamp, coupon_redemption_timestamp)
SELECT u.user_id, d.discount_id, 'UNUSED', CURRENT_TIMESTAMP - INTERVAL '1 hour', NULL
FROM users u
JOIN discounts d ON d.discount_name = 'Mock - Bebidas 2x1'
WHERE u.user_email = 'valentina.cliente@savorystudio.test'
  AND NOT EXISTS (
      SELECT 1 FROM coupon c
      WHERE c.user_id = u.user_id
        AND c.discount_id = d.discount_id
  );

INSERT INTO coupon (user_id, discount_id, coupon_status, coupon_claim_timestamp, coupon_redemption_timestamp)
SELECT u.user_id, d.discount_id, 'UNUSED', CURRENT_TIMESTAMP - INTERVAL '5 days', NULL
FROM users u
JOIN discounts d ON d.discount_name = 'Mock - Descuento de 10000'
WHERE u.user_email = 'lucia.cliente@savorystudio.test'
  AND NOT EXISTS (
      SELECT 1 FROM coupon c
      WHERE c.user_id = u.user_id
        AND c.discount_id = d.discount_id
  );

INSERT INTO coupon (user_id, discount_id, coupon_status, coupon_claim_timestamp, coupon_redemption_timestamp)
SELECT u.user_id, d.discount_id, 'USED', CURRENT_TIMESTAMP - INTERVAL '5 days', CURRENT_TIMESTAMP - INTERVAL '4 days'
FROM users u
JOIN discounts d ON d.discount_name = 'Mock - Postres 3x2'
WHERE u.user_email = 'lucia.cliente@savorystudio.test'
  AND NOT EXISTS (
      SELECT 1 FROM coupon c
      WHERE c.user_id = u.user_id
        AND c.discount_id = d.discount_id
  );

INSERT INTO coupon (user_id, discount_id, coupon_status, coupon_claim_timestamp, coupon_redemption_timestamp)
SELECT u.user_id, d.discount_id, 'USED', CURRENT_TIMESTAMP - INTERVAL '3 days', CURRENT_TIMESTAMP - INTERVAL '2 days'
FROM users u
JOIN discounts d ON d.discount_name = 'Mock - Postres 3x2'
WHERE u.user_email = 'santiago.cliente@savorystudio.test'
  AND NOT EXISTS (
      SELECT 1 FROM coupon c
      WHERE c.user_id = u.user_id
        AND c.discount_id = d.discount_id
  );

INSERT INTO coupon (user_id, discount_id, coupon_status, coupon_claim_timestamp, coupon_redemption_timestamp)
SELECT u.user_id, d.discount_id, 'UNUSED', CURRENT_TIMESTAMP - INTERVAL '2 days', NULL
FROM users u
JOIN discounts d ON d.discount_name = 'Mock - Cena 15%'
WHERE u.user_email = 'santiago.cliente@savorystudio.test'
  AND NOT EXISTS (
      SELECT 1 FROM coupon c
      WHERE c.user_id = u.user_id
        AND c.discount_id = d.discount_id
  );

INSERT INTO discounts (
    discount_restaurant_id, discount_view_amount, discount_name, discount_status,
    discount_applicable_products, discount_type, discount_amount,
    discount_customer_takes_amount, discount_customer_pays_amount,
    discount_start_timestamp, discount_end_timestamp,
    discount_max_uses, discount_max_use_amount_per_user,
    discount_additional_use_conditions
)
SELECT r.restaurant_id, 7, 'Mock - RN03 Fecha vencida', 'Active',
       'Entradas seleccionadas', 'FIXED_VALUE', 5000.00, NULL, NULL,
       CURRENT_TIMESTAMP - INTERVAL '10 days', CURRENT_TIMESTAMP - INTERVAL '1 day',
       20, 5, 'Escenario aislado RN-03: fecha vencida con estado Active.'
FROM restaurants r
WHERE r.restaurant_name = 'Mock - La Mesa del Centro'
  AND NOT EXISTS (
      SELECT 1 FROM discounts d
      WHERE d.discount_restaurant_id = r.restaurant_id
        AND d.discount_name = 'Mock - RN03 Fecha vencida'
  );

INSERT INTO discounts (
    discount_restaurant_id, discount_view_amount, discount_name, discount_status,
    discount_applicable_products, discount_type, discount_amount,
    discount_customer_takes_amount, discount_customer_pays_amount,
    discount_start_timestamp, discount_end_timestamp,
    discount_max_uses, discount_max_use_amount_per_user,
    discount_additional_use_conditions
)
SELECT r.restaurant_id, 12, 'Mock - RN05 Limite general agotado', 'Active',
       'Platos seleccionados', 'PERCENTAGE', 10.00, NULL, NULL,
       CURRENT_TIMESTAMP - INTERVAL '7 days', CURRENT_TIMESTAMP + INTERVAL '14 days',
       1, 5, 'Escenario aislado RN-05: un canje utilizado agota el limite general.'
FROM restaurants r
WHERE r.restaurant_name = 'Mock - Patio del Sabor'
  AND NOT EXISTS (
      SELECT 1 FROM discounts d
      WHERE d.discount_restaurant_id = r.restaurant_id
        AND d.discount_name = 'Mock - RN05 Limite general agotado'
  );

INSERT INTO discounts (
    discount_restaurant_id, discount_view_amount, discount_name, discount_status,
    discount_applicable_products, discount_type, discount_amount,
    discount_customer_takes_amount, discount_customer_pays_amount,
    discount_start_timestamp, discount_end_timestamp,
    discount_max_uses, discount_max_use_amount_per_user,
    discount_additional_use_conditions
)
SELECT r.restaurant_id, 16, 'Mock - RN04 Limite por usuario alcanzado', 'Active',
       'Bebidas seleccionadas', 'PERCENTAGE', 25.00, NULL, NULL,
       CURRENT_TIMESTAMP - INTERVAL '7 days', CURRENT_TIMESTAMP + INTERVAL '14 days',
       10, 1, 'Escenario aislado RN-04: un usuario alcanza su maximo individual.'
FROM restaurants r
WHERE r.restaurant_name = 'Mock - La Mesa del Centro'
  AND NOT EXISTS (
      SELECT 1 FROM discounts d
      WHERE d.discount_restaurant_id = r.restaurant_id
        AND d.discount_name = 'Mock - RN04 Limite por usuario alcanzado'
  );

INSERT INTO coupon (user_id, discount_id, coupon_status, coupon_claim_timestamp, coupon_redemption_timestamp)
SELECT u.user_id, d.discount_id, 'UNUSED', CURRENT_TIMESTAMP - INTERVAL '2 days', NULL
FROM users u
JOIN discounts d ON d.discount_name = 'Mock - RN03 Fecha vencida'
WHERE u.user_email = 'valentina.cliente@savorystudio.test'
  AND NOT EXISTS (
      SELECT 1 FROM coupon c
      WHERE c.user_id = u.user_id
        AND c.discount_id = d.discount_id
  );

INSERT INTO coupon (user_id, discount_id, coupon_status, coupon_claim_timestamp, coupon_redemption_timestamp)
SELECT u.user_id, d.discount_id, 'USED', CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '1 day'
FROM users u
JOIN discounts d ON d.discount_name = 'Mock - RN05 Limite general agotado'
WHERE u.user_email = 'lucia.cliente@savorystudio.test'
  AND NOT EXISTS (
      SELECT 1 FROM coupon c
      WHERE c.user_id = u.user_id
        AND c.discount_id = d.discount_id
  );

INSERT INTO coupon (user_id, discount_id, coupon_status, coupon_claim_timestamp, coupon_redemption_timestamp)
SELECT u.user_id, d.discount_id, 'UNUSED', CURRENT_TIMESTAMP - INTERVAL '1 hour', NULL
FROM users u
JOIN discounts d ON d.discount_name = 'Mock - RN05 Limite general agotado'
WHERE u.user_email = 'santiago.cliente@savorystudio.test'
  AND NOT EXISTS (
      SELECT 1 FROM coupon c
      WHERE c.user_id = u.user_id
        AND c.discount_id = d.discount_id
  );

INSERT INTO coupon (user_id, discount_id, coupon_status, coupon_claim_timestamp, coupon_redemption_timestamp)
SELECT u.user_id, d.discount_id, 'USED', CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '1 day'
FROM users u
JOIN discounts d ON d.discount_name = 'Mock - RN04 Limite por usuario alcanzado'
WHERE u.user_email = 'santiago.cliente@savorystudio.test'
  AND NOT EXISTS (
      SELECT 1 FROM coupon c
      WHERE c.user_id = u.user_id
        AND c.discount_id = d.discount_id
        AND c.coupon_status = 'USED'
  );

INSERT INTO coupon (user_id, discount_id, coupon_status, coupon_claim_timestamp, coupon_redemption_timestamp)
SELECT u.user_id, d.discount_id, 'UNUSED', CURRENT_TIMESTAMP - INTERVAL '1 hour', NULL
FROM users u
JOIN discounts d ON d.discount_name = 'Mock - RN04 Limite por usuario alcanzado'
WHERE u.user_email = 'santiago.cliente@savorystudio.test'
  AND NOT EXISTS (
      SELECT 1 FROM coupon c
      WHERE c.user_id = u.user_id
        AND c.discount_id = d.discount_id
        AND c.coupon_status = 'UNUSED'
  );

COMMIT;
