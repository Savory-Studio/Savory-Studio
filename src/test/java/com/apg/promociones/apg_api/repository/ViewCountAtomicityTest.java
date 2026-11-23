package com.apg.promociones.apg_api.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate"
}, showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ViewCountAtomicityTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.11-alpine")
            .withInitScript("sql/apg42-schema.sql");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
    }

    @Autowired
    private DiscountRepository discountRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private int promotionId;

    @BeforeEach
    void seed() {
        int userId = jdbc.queryForObject("""
                INSERT INTO users (user_first_name, user_last_name, user_birthday, user_email, user_password_hash)
                VALUES ('Test', 'User', DATE '2000-01-01', 'view-test@example.test', 'hash')
                RETURNING user_id
                """, Integer.class);
        int restaurantId = jdbc.queryForObject("""
                INSERT INTO restaurants (restaurant_name, restaurant_owner_id, restaurant_full_address)
                VALUES ('View Test Restaurant', ?, 'Address') RETURNING restaurant_id
                """, Integer.class, userId);
        promotionId = jdbc.queryForObject("""
                INSERT INTO discounts (discount_restaurant_id, discount_name, discount_status,
                    discount_applicable_products, discount_type, discount_amount,
                    discount_view_amount, discount_start_timestamp, discount_end_timestamp)
                VALUES (?, 'View Test Promo', CAST('Active' AS discount_status_enum),
                    'Products', 'PERCENTAGE', 20, 0,
                    CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP + INTERVAL '1 day')
                RETURNING discount_id
                """, Integer.class, restaurantId);
    }

    @Test
    void incrementViewCountAddsExactlyOne() {
        assertEquals(0, getViewCount());

        int updated = discountRepository.incrementViewCount(promotionId);

        assertEquals(1, updated);
        assertEquals(1, getViewCount());
    }

    @Test
    void multipleIncrementsAccumulateCorrectly() {
        for (int i = 0; i < 5; i++) {
            discountRepository.incrementViewCount(promotionId);
        }

        assertEquals(5, getViewCount());
    }

    @Test
    void incrementReturnsZeroForNonExistentPromotion() {
        int updated = discountRepository.incrementViewCount(Integer.MAX_VALUE);

        assertEquals(0, updated);
    }

    @Test
    void incrementDoesNotAffectOtherColumns() {
        String statusBefore = jdbc.queryForObject(
                "SELECT discount_status::text FROM discounts WHERE discount_id = ?",
                String.class, promotionId);
        String nameBefore = jdbc.queryForObject(
                "SELECT discount_name FROM discounts WHERE discount_id = ?",
                String.class, promotionId);

        discountRepository.incrementViewCount(promotionId);

        assertEquals(statusBefore, jdbc.queryForObject(
                "SELECT discount_status::text FROM discounts WHERE discount_id = ?",
                String.class, promotionId));
        assertEquals(nameBefore, jdbc.queryForObject(
                "SELECT discount_name FROM discounts WHERE discount_id = ?",
                String.class, promotionId));
    }

    @Test
    void queryingMetricsDoesNotIncrementViews() {
        discountRepository.incrementViewCount(promotionId);
        int countAfterIncrement = getViewCount();

        discountRepository.findPromotionMetrics();
        discountRepository.findPromotionMetrics();

        assertEquals(countAfterIncrement, getViewCount());
    }

    private int getViewCount() {
        return jdbc.queryForObject(
                "SELECT discount_view_amount FROM discounts WHERE discount_id = ?",
                Integer.class, promotionId);
    }
}
