package com.apg.promociones.apg_api.repository;

import com.apg.promociones.apg_api.dto.PromotionMetricsProjection;
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

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.generate_statistics=true"
}, showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class PromotionMetricsQueryTest {

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
    private CouponRepository couponRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private int customerA;
    private int customerB;
    private int restaurant1;
    private int restaurant2;

    @BeforeEach
    void seed() {
        customerA = createUser("alice");
        customerB = createUser("bob");
        restaurant1 = createRestaurant("Grill House", customerA);
        restaurant2 = createRestaurant("Sushi Place", customerB);
    }

    @Test
    void returnsZerosForPromotionWithoutCoupons() {
        int promoId = createDiscount(restaurant1, "No-coupon promo", "Active", 42);

        List<PromotionMetricsProjection> metrics = discountRepository.findPromotionMetrics();

        PromotionMetricsProjection m = findById(metrics, promoId);
        assertNotNull(m);
        assertEquals("No-coupon promo", m.getPromotionName());
        assertEquals(restaurant1, m.getRestaurantId());
        assertEquals(42, m.getViewCount());
        assertEquals(0L, m.getTotalClaimed());
        assertEquals(0L, m.getTotalRedeemed());
    }

    @Test
    void countsUnusedCouponsAsClaimedButNotRedeemed() {
        int promoId = createDiscount(restaurant1, "Unused promo", "Active", 10);
        createCoupon(customerA, promoId, "UNUSED");
        createCoupon(customerB, promoId, "UNUSED");

        PromotionMetricsProjection m = findById(discountRepository.findPromotionMetrics(), promoId);

        assertEquals(10, m.getViewCount());
        assertEquals(2L, m.getTotalClaimed());
        assertEquals(0L, m.getTotalRedeemed());
    }

    @Test
    void countsUsedCouponsInBothClaimedAndRedeemed() {
        int promoId = createDiscount(restaurant1, "Mixed promo", "Active", 5);
        createCoupon(customerA, promoId, "USED");
        createCoupon(customerB, promoId, "UNUSED");

        PromotionMetricsProjection m = findById(discountRepository.findPromotionMetrics(), promoId);

        assertEquals(5, m.getViewCount());
        assertEquals(2L, m.getTotalClaimed());
        assertEquals(1L, m.getTotalRedeemed());
    }

    @Test
    void doesNotDoubleCountSameRedemption() {
        int promoId = createDiscount(restaurant1, "Single use", "Active", 0);
        createCoupon(customerA, promoId, "USED");

        PromotionMetricsProjection m = findById(discountRepository.findPromotionMetrics(), promoId);

        assertEquals(1L, m.getTotalClaimed());
        assertEquals(1L, m.getTotalRedeemed());
    }

    @Test
    void aggregatesAcrossMultipleRestaurants() {
        int promo1 = createDiscount(restaurant1, "R1 promo", "Active", 100);
        int promo2 = createDiscount(restaurant2, "R2 promo", "Active", 200);
        createCoupon(customerA, promo1, "USED");
        createCoupon(customerB, promo1, "USED");
        createCoupon(customerA, promo2, "UNUSED");

        List<PromotionMetricsProjection> metrics = discountRepository.findPromotionMetrics();

        PromotionMetricsProjection m1 = findById(metrics, promo1);
        assertEquals(100, m1.getViewCount());
        assertEquals(2L, m1.getTotalClaimed());
        assertEquals(2L, m1.getTotalRedeemed());

        PromotionMetricsProjection m2 = findById(metrics, promo2);
        assertEquals(200, m2.getViewCount());
        assertEquals(1L, m2.getTotalClaimed());
        assertEquals(0L, m2.getTotalRedeemed());
    }

    @Test
    void filtersByRestaurantId() {
        int promo1 = createDiscount(restaurant1, "R1 only", "Active", 10);
        createDiscount(restaurant2, "R2 only", "Active", 20);
        createCoupon(customerA, promo1, "USED");

        List<PromotionMetricsProjection> metrics =
                discountRepository.findPromotionMetricsByRestaurantId(restaurant1);

        assertEquals(1, metrics.size());
        assertEquals(promo1, metrics.get(0).getPromotionId());
        assertEquals(1L, metrics.get(0).getTotalRedeemed());
    }

    @Test
    void includesFinishedPromotionsInMetrics() {
        int promoId = createDiscount(restaurant1, "Old promo", "Finished", 500);
        createCoupon(customerA, promoId, "USED");

        PromotionMetricsProjection m = findById(discountRepository.findPromotionMetrics(), promoId);

        assertEquals(500, m.getViewCount());
        assertEquals(1L, m.getTotalClaimed());
        assertEquals(1L, m.getTotalRedeemed());
    }

    @Test
    void countByDiscountIdReturnsTotalCouponsRegardlessOfStatus() {
        int promoId = createDiscount(restaurant1, "Count promo", "Active", 0);
        createCoupon(customerA, promoId, "USED");
        createCoupon(customerB, promoId, "UNUSED");

        assertEquals(2L, couponRepository.countByDiscount_Id(promoId));
    }

    @Test
    void countByDiscountIdReturnsZeroWhenNoCoupons() {
        int promoId = createDiscount(restaurant1, "Empty promo", "Active", 0);

        assertEquals(0L, couponRepository.countByDiscount_Id(promoId));
    }

    @Test
    void metricsQueryDoesNotMutateData() {
        int promoId = createDiscount(restaurant1, "Safe promo", "Active", 15);
        int couponId = createCoupon(customerA, promoId, "UNUSED");

        discountRepository.findPromotionMetrics();
        discountRepository.findPromotionMetricsByRestaurantId(restaurant1);
        couponRepository.countByDiscount_Id(promoId);

        assertEquals("UNUSED", jdbc.queryForObject(
                "SELECT coupon_status::text FROM coupon WHERE coupon_id = ?",
                String.class, couponId));
        assertEquals(15, jdbc.queryForObject(
                "SELECT discount_view_amount FROM discounts WHERE discount_id = ?",
                Integer.class, promoId));
    }

    private PromotionMetricsProjection findById(List<PromotionMetricsProjection> metrics, int promotionId) {
        return metrics.stream()
                .filter(m -> m.getPromotionId().equals(promotionId))
                .findFirst()
                .orElse(null);
    }

    private int createUser(String name) {
        return jdbc.queryForObject("""
                INSERT INTO users (user_first_name, user_last_name, user_birthday, user_email, user_password_hash)
                VALUES (?, 'Test', DATE '2000-01-01', ?, 'test-only-hash') RETURNING user_id
                """, Integer.class, name, name + "-" + UUID.randomUUID() + "@example.test");
    }

    private int createRestaurant(String name, int ownerId) {
        return jdbc.queryForObject("""
                INSERT INTO restaurants (restaurant_name, restaurant_owner_id, restaurant_full_address)
                VALUES (?, ?, 'Test address') RETURNING restaurant_id
                """, Integer.class, name, ownerId);
    }

    private int createDiscount(int restaurantId, String name, String status, int viewAmount) {
        return jdbc.queryForObject("""
                INSERT INTO discounts (discount_restaurant_id, discount_name, discount_status,
                    discount_applicable_products, discount_type, discount_amount,
                    discount_view_amount,
                    discount_start_timestamp, discount_end_timestamp)
                VALUES (?, ?, CAST(? AS discount_status_enum), 'Test products', 'PERCENTAGE', 20,
                    ?,
                    CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP + INTERVAL '2 days')
                RETURNING discount_id
                """, Integer.class, restaurantId, name, status, viewAmount);
    }

    private int createCoupon(int userId, int discountId, String status) {
        return jdbc.queryForObject("""
                INSERT INTO coupon (user_id, discount_id, coupon_status, coupon_hash,
                    coupon_claim_timestamp, coupon_redemption_timestamp)
                VALUES (?, ?, CAST(? AS coupon_status_enum), ?, CURRENT_TIMESTAMP - INTERVAL '1 day',
                    CASE WHEN ? = 'USED' THEN CURRENT_TIMESTAMP ELSE NULL END)
                RETURNING coupon_id
                """, Integer.class, userId, discountId, status, UUID.randomUUID().toString(), status);
    }
}
