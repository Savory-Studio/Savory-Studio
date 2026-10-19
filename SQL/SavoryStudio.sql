CREATE DATABASE SavoryStudio;
USE SavoryStudio;

CREATE TABLE users (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    user_first_name VARCHAR(255) NOT NULL,
    user_second_name VARCHAR(255),
    user_last_name VARCHAR(255) NOT NULL,
    user_birthday DATE NOT NULL,
    user_join_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    user_email VARCHAR(255) NOT NULL UNIQUE,
    user_password_hash VARCHAR(255) NOT NULL
);

CREATE TABLE restaurants(
    restaurant_id INT AUTO_INCREMENT PRIMARY KEY,
    restaurant_name VARCHAR(255) NOT NULL,
    restaurant_owner_id INT NOT NULL,
    restaurant_description TEXT,
    restaurant_full_address VARCHAR(255) NOT NULL,
    restaurant_creation_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (restaurant_owner_id) REFERENCES users(user_id) ON UPDATE CASCADE ON DELETE RESTRICT
);

CREATE TABLE discounts(
    discount_id INT AUTO_INCREMENT PRIMARY KEY,
    discount_restaurant_id INT NOT NULL,
    discount_view_amount INT NOT NULL DEFAULT 0 CHECK(discount_view_amount >= 0),
    discount_name VARCHAR(255) NOT NULL,
    discount_status ENUM('Active','Suspended','Finished') NOT NULL DEFAULT 'Active',
    discount_applicable_products TEXT NOT NULL,
    discount_type ENUM('PERCENTAGE','FIXED_VALUE','IXJ') NOT NULL, # IXJ es para soportar promociones de tipo pague x lleve y, i=x, y=j
    discount_amount DECIMAL(10,2),
    discount_customer_takes_amount INT, # I: Cliente lleva esta cantidad de producto. Solo aplica en caso de que el descuento sea de tipo IXJ
    discount_customer_pays_amount INT, # J: Cliente paga esta cantidad de producto. Solo aplica en caso de que el descuento sea de tipo IXJ
    discount_start_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    discount_end_timestamp TIMESTAMP,
    discount_max_uses INT CHECK(discount_max_uses > 0),
    discount_max_use_amount_per_user INT CHECK(discount_max_use_amount_per_user > 0),
    discount_image_url VARCHAR(255),
    discount_additional_use_conditions TEXT, # En caso de que la promoción requiera checkear otra clase de condiciones más abstractas, como por ejemplo "El usuario debe traer 5 tapas de nuestra cerveza para poder canjear"
    # Lo de las condiciones de uso se maneja con todos los campos que determinan las condiciones de validez para la promo

    CONSTRAINT chk_ixj CHECK (
        discount_type <> 'IXJ'
        OR (
            discount_customer_takes_amount IS NOT NULL
            AND discount_customer_pays_amount IS NOT NULL
            AND discount_customer_takes_amount > discount_customer_pays_amount
            AND discount_customer_pays_amount >= 1
        )
    ),

    CHECK (
        discount_type = 'IXJ'
        OR (
            discount_customer_takes_amount IS NULL
            AND discount_customer_pays_amount IS NULL
        )
    ),

    CHECK (
        (discount_type = 'IXJ' AND discount_amount IS NULL)
        OR
        (discount_type = 'PERCENTAGE' AND discount_amount > 0 AND discount_amount <= 100)
        OR
        (discount_type = 'FIXED_VALUE' AND discount_amount > 0)
    ),

    CHECK (
        discount_end_timestamp IS NULL
        OR discount_end_timestamp > discount_start_timestamp
    ),

    CHECK (
        discount_end_timestamp IS NOT NULL
        OR discount_max_uses IS NOT NULL
    ),

    FOREIGN KEY (discount_restaurant_id) REFERENCES restaurants(restaurant_id) ON UPDATE CASCADE ON DELETE RESTRICT
);

CREATE TABLE coupon(
    coupon_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    discount_id INT NOT NULL,
    coupon_status ENUM('UNUSED','USED') NOT NULL DEFAULT 'UNUSED',
    coupon_claim_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    coupon_redemption_timestamp TIMESTAMP,

    CHECK (
        (coupon_status = 'UNUSED' AND coupon_redemption_timestamp IS NULL)
        OR
        (
            coupon_status = 'USED'
            AND coupon_redemption_timestamp IS NOT NULL
            AND coupon_redemption_timestamp >= coupon_claim_timestamp
        )
    ),

    FOREIGN KEY (user_id) REFERENCES users(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    FOREIGN KEY (discount_id) REFERENCES discounts(discount_id) ON UPDATE CASCADE ON DELETE RESTRICT
);