package com.apg.promociones.apg_api.repository;

import com.apg.promociones.apg_api.model.Coupon;
import com.apg.promociones.apg_api.model.Discount;
import com.apg.promociones.apg_api.model.enums.CouponStatus;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.generate_statistics=true"
}, showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class PromotionRepositoryTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.11-alpine")
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

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private int customerId;
    private int otherCustomerId;
    private int restaurantId;

    @BeforeEach
    void seedCustomersAndRestaurant() {
        customerId = createUser("customer");
        otherCustomerId = createUser("other");
        restaurantId = jdbc.queryForObject("""
                INSERT INTO restaurants (restaurant_name, restaurant_owner_id, restaurant_full_address)
                VALUES ('Test restaurant', ?, 'Test address') RETURNING restaurant_id
                """, Integer.class, customerId);
    }

    @Test
    void returnsCurrentUnlimitedPromotion() {
        int id = createDiscount("Active", null);

        List<Discount> result = discountRepository.findActivePromotions();

        assertEquals(List.of(id), result.stream().map(Discount::getId).toList());
        assertEquals(restaurantId, result.getFirst().getRestaurant().getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Suspended", "Finished"})
    void excludesInactivePromotions(String status) {
        createDiscount(status, null);

        assertTrue(discountRepository.findActivePromotions().isEmpty());
    }

    @Test
    void excludesActiveButExpiredPromotion() {
        int id = createDiscount("Active", null);
        jdbc.update("UPDATE discounts SET discount_end_timestamp = CURRENT_TIMESTAMP - INTERVAL '1 day' WHERE discount_id = ?", id);

        assertTrue(discountRepository.findActivePromotions().isEmpty());
    }

    @Test
    void excludesFuturePromotion() {
        int id = createDiscount("Active", null);
        jdbc.update("UPDATE discounts SET discount_start_timestamp = CURRENT_TIMESTAMP + INTERVAL '1 day' WHERE discount_id = ?", id);

        assertTrue(discountRepository.findActivePromotions().isEmpty());
    }

    @Test
    void includesPromotionAtExactStart() {
        int id = createDiscount("Active", null);
        jdbc.update("UPDATE discounts SET discount_start_timestamp = CURRENT_TIMESTAMP WHERE discount_id = ?", id);

        assertEquals(List.of(id), activeIds());
    }

    @Test
    void includesPromotionAtExactEnd() {
        int id = createDiscount("Active", null);
        jdbc.update("UPDATE discounts SET discount_end_timestamp = CURRENT_TIMESTAMP WHERE discount_id = ?", id);

        assertEquals(List.of(id), activeIds());
    }

    @Test
    void includesPromotionWithoutStartDate() {
        int id = createDiscount("Active", null);
        jdbc.update("UPDATE discounts SET discount_start_timestamp = NULL WHERE discount_id = ?", id);

        assertEquals(List.of(id), activeIds());
    }

    @Test
    void includesPromotionWithoutEndDateWhenStockIsAvailable() {
        int id = createDiscount("Active", 1);
        jdbc.update("UPDATE discounts SET discount_end_timestamp = NULL WHERE discount_id = ?", id);

        assertEquals(List.of(id), activeIds());
    }

    @ParameterizedTest
    @ValueSource(strings = {"UTC", "America/Bogota"})
    void usesDatabaseTimeAcrossSessionTimeZones(String timezone) {
        jdbc.queryForObject("SELECT set_config('TimeZone', ?, true)", String.class, timezone);
        int currentId = createDiscount("Active", null);
        int expiredId = createDiscount("Active", null);
        jdbc.update("UPDATE discounts SET discount_end_timestamp = CURRENT_TIMESTAMP - INTERVAL '1 minute' WHERE discount_id = ?", expiredId);

        assertEquals(List.of(currentId), activeIds());
    }

    @Test
    void countsOnlyUsedCouponsAcrossCustomersAndDoesNotDuplicatePromotions() {
        int id = createDiscount("Active", 2);
        createCoupon(customerId, id, "USED");
        createCoupon(customerId, id, "UNUSED");
        createCoupon(otherCustomerId, id, "UNUSED");

        assertEquals(List.of(id), activeIds());

        createCoupon(otherCustomerId, id, "USED");

        assertTrue(discountRepository.findActivePromotions().isEmpty());
    }

    @Test
    void excludesPromotionsOverTheirGlobalLimit() {
        int id = createDiscount("Active", 1);
        createCoupon(customerId, id, "USED");
        createCoupon(otherCustomerId, id, "USED");

        assertTrue(discountRepository.findActivePromotions().isEmpty());
    }

    @Test
    void keepsUnlimitedPromotionAvailableAfterRedemptions() {
        int id = createDiscount("Active", null);
        createCoupon(customerId, id, "USED");

        assertEquals(List.of(id), activeIds());
    }

    @Test
    void returnsOnlyRequestedCustomersCouponsIncludingHistory() {
        int discountId = createDiscount("Active", 1);
        int unusedId = createCoupon(customerId, discountId, "UNUSED");
        int usedId = createCoupon(customerId, discountId, "USED");
        createCoupon(otherCustomerId, discountId, "UNUSED");

        List<Coupon> result = couponRepository.findByUser_Id(customerId);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(c -> c.getUser().getId().equals(customerId)));
        assertTrue(result.stream().map(Coupon::getId).toList().containsAll(List.of(unusedId, usedId)));
        assertTrue(result.stream().map(Coupon::getStatus).toList().containsAll(List.of(CouponStatus.UNUSED, CouponStatus.USED)));
        assertTrue(result.stream().allMatch(c -> c.getCouponHash() != null));
    }

    @Test
    void returnsEmptyListForCustomerWithoutCoupons() {
        createCoupon(otherCustomerId, createDiscount("Active", null), "UNUSED");

        assertTrue(couponRepository.findByUser_Id(customerId).isEmpty());
        assertTrue(couponRepository.findByUser_Id(Integer.MAX_VALUE).isEmpty());
    }

    @Test
    void preservesExistingCouponHashAndRedemptionQueries() {
        int discountId = createDiscount("Active", 3);
        int couponId = createCoupon(customerId, discountId, "USED");
        createCoupon(otherCustomerId, discountId, "UNUSED");
        Coupon coupon = couponRepository.findById(couponId).orElseThrow();

        assertEquals(couponId, couponRepository.findByCouponHash(coupon.getCouponHash()).orElseThrow().getId());
        assertEquals(1, couponRepository.countByDiscountAndStatus(coupon.getDiscount(), CouponStatus.USED));
        assertEquals(1, couponRepository.countByUserAndDiscountAndStatus(coupon.getUser(), coupon.getDiscount(), CouponStatus.USED));
        assertTrue(couponRepository.existsUnusedByUserAndDiscount(otherCustomerId, discountId));
    }

    @Test
    void listingDoesNotChangeCouponOrPromotionState() {
        int id = createDiscount("Active", 2);
        int couponId = createCoupon(customerId, id, "UNUSED");

        discountRepository.findActivePromotions();
        couponRepository.findByUser_Id(customerId);

        assertEquals("UNUSED", jdbc.queryForObject("SELECT coupon_status::text FROM coupon WHERE coupon_id = ?", String.class, couponId));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM coupon WHERE coupon_redemption_timestamp IS NOT NULL", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT discount_max_uses FROM discounts WHERE discount_id = ?", Integer.class, id));
        assertEquals("Active", jdbc.queryForObject("SELECT discount_status::text FROM discounts WHERE discount_id = ?", String.class, id));
    }

    @Test
    void filtersExpiredVolumeInOneDatabaseQuery() {
        int id = createDiscount("Active", 1);
        jdbc.update("""
                INSERT INTO discounts (discount_restaurant_id, discount_name, discount_applicable_products,
                    discount_type, discount_amount, discount_start_timestamp, discount_end_timestamp)
                SELECT ?, 'Expired ' || n, 'Test menu', 'PERCENTAGE', 20,
                    CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '1 day'
                FROM generate_series(1, 10000) n
                """, restaurantId);
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        assertEquals(List.of(id), activeIds());
        assertEquals(1, statistics.getPrepareStatementCount());
    }

    private List<Integer> activeIds() {
        return discountRepository.findActivePromotions().stream().map(Discount::getId).toList();
    }

    private int createUser(String name) {
        return jdbc.queryForObject("""
                INSERT INTO users (user_first_name, user_last_name, user_birthday, user_email, user_password_hash)
                VALUES (?, 'Test', DATE '2000-01-01', ?, 'test-only-hash') RETURNING user_id
                """, Integer.class, name, name + "@example.test");
    }

    private int createDiscount(String status, Integer maxUses) {
        return jdbc.queryForObject("""
                INSERT INTO discounts (discount_restaurant_id, discount_name, discount_status,
                    discount_applicable_products, discount_type, discount_amount,
                    discount_start_timestamp, discount_end_timestamp, discount_max_uses)
                VALUES (?, 'Test promotion', CAST(? AS discount_status_enum), 'Test menu', 'PERCENTAGE', 20,
                    CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP + INTERVAL '2 days', ?)
                RETURNING discount_id
                """, Integer.class, restaurantId, status, maxUses);
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
