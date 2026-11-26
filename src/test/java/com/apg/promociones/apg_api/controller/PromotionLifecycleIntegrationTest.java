package com.apg.promociones.apg_api.controller;

import com.apg.promociones.apg_api.model.User;
import com.apg.promociones.apg_api.service.LoginServices.JwtService;
import com.apg.promociones.apg_api.service.PromotionLifecycleService;
import com.apg.promociones.apg_api.service.PromotionLifecycleService.PromotionCloseSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Timestamp;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.simulated-auth.enabled=true",
        "app.promotion-lifecycle.close-interval-ms=200"
})
@AutoConfigureMockMvc
@Testcontainers
class PromotionLifecycleIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.11-alpine")
            .withInitScript("sql/apg42-schema.sql");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PromotionLifecycleService promotionLifecycleService;

    @Test
    void scheduledClosureMarksExpiredActivePromotionsAsFinishedWithoutRequestsAndPreservesHistoryAndSuspended()
            throws Exception {
        int ownerId = createUser("owner-sched-exp");
        int restaurantId = createRestaurant(ownerId);

        int expiredActiveId = createPromotionWithEndOffset(restaurantId, "Active", "-1 minute", null);
        int futureActiveId = createPromotionWithEndOffset(restaurantId, "Active", "+2 days", null);
        int expiredSuspendedId = createPromotionWithEndOffset(restaurantId, "Suspended", "-1 minute", null);

        String hashUnused = UUID.randomUUID().toString();
        String hashUsed = UUID.randomUUID().toString();
        int unusedCouponId = createCoupon(createUser("cust-exp-1"), expiredActiveId, "UNUSED", hashUnused);
        int usedCouponId = createCoupon(createUser("cust-exp-2"), expiredActiveId, "USED", hashUsed);
        Timestamp originalUsedTimestamp = redemptionTimestamp(usedCouponId);

        // Wait for the @Scheduled task without sending any HTTP request or calling the service manually
        long deadline = System.currentTimeMillis() + 5_000L;
        while (!"Finished".equals(promotionStatus(expiredActiveId)) && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }

        assertEquals("Finished", promotionStatus(expiredActiveId));
        assertEquals("Active", promotionStatus(futureActiveId));
        assertEquals("Suspended", promotionStatus(expiredSuspendedId));

        // Coupon history must remain intact
        assertEquals("UNUSED", couponStatus(unusedCouponId));
        assertNull(redemptionTimestamp(unusedCouponId));
        assertEquals(hashUnused, couponHash(unusedCouponId));
        assertEquals("USED", couponStatus(usedCouponId));
        assertEquals(originalUsedTimestamp, redemptionTimestamp(usedCouponId));
        assertEquals(hashUsed, couponHash(usedCouponId));

        // Subsequent redemption must be rejected with 409 Conflict and leave coupon UNUSED
        mvc.perform(post("/api/canjes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"couponId\":" + unusedCouponId + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Esta promoción ya no está disponible."));

        assertEquals("UNUSED", couponStatus(unusedCouponId));
        assertNull(redemptionTimestamp(unusedCouponId));
    }

    @Test
    void lastAvailableRedemptionImmediatelyTransitionsActivePromotionToFinishedAndRejectsSubsequentRedemption()
            throws Exception {
        int ownerId = createUser("owner-max-uses");
        int restaurantId = createRestaurant(ownerId);
        int discountId = createPromotionWithEndOffset(restaurantId, "Active", "+2 days", 2);

        int coupon1 = createCoupon(createUser("cust-use-1"), discountId, "UNUSED", UUID.randomUUID().toString());
        int coupon2 = createCoupon(createUser("cust-use-2"), discountId, "UNUSED", UUID.randomUUID().toString());
        int coupon3 = createCoupon(createUser("cust-use-3"), discountId, "UNUSED", UUID.randomUUID().toString());

        // UNUSED coupons do not exhaust the quota; only USED redemptions count
        PromotionCloseSummary preCheck = promotionLifecycleService.closeExpiredAndExhaustedPromotions();
        assertEquals(0, preCheck.exhaustedClosed());
        assertEquals("Active", promotionStatus(discountId));

        String token = tokenFor(ownerId);

        // 1st redemption (1 of 2): promotion stays Active
        mvc.perform(post("/api/canjes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"couponId\":" + coupon1 + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("USED"));
        assertEquals("Active", promotionStatus(discountId));

        // 2nd redemption (2 of 2 = max_uses): promotion immediately transitions to Finished
        mvc.perform(post("/api/canjes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"couponId\":" + coupon2 + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("USED"));
        assertEquals("Finished", promotionStatus(discountId));

        Timestamp c1Ts = redemptionTimestamp(coupon1);
        Timestamp c2Ts = redemptionTimestamp(coupon2);

        // 3rd redemption attempt must be rejected with 409 and preserve all coupon rows
        mvc.perform(post("/api/canjes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"couponId\":" + coupon3 + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("agotado")));

        assertEquals("UNUSED", couponStatus(coupon3));
        assertNull(redemptionTimestamp(coupon3));
        assertEquals("USED", couponStatus(coupon1));
        assertEquals("USED", couponStatus(coupon2));
        assertEquals(c1Ts, redemptionTimestamp(coupon1));
        assertEquals(c2Ts, redemptionTimestamp(coupon2));

        // Re-running closure on an already Finished promotion is idempotent
        PromotionCloseSummary postCheck = promotionLifecycleService.closeExpiredAndExhaustedPromotions();
        assertEquals(0, postCheck.totalClosed());
    }

    @Test
    void lifecycleClosureReconcilesExhaustedActivePromotionWhilePreservingSuspended() {
        int ownerId = createUser("owner-reconcile");
        int restaurantId = createRestaurant(ownerId);

        int exhaustedActiveId = createPromotionWithEndOffset(restaurantId, "Active", "+2 days", 1);
        int exhaustedSuspendedId = createPromotionWithEndOffset(restaurantId, "Suspended", "+2 days", 1);

        int cActive = createCoupon(createUser("cust-rec-1"), exhaustedActiveId, "USED", UUID.randomUUID().toString());
        int cSusp = createCoupon(createUser("cust-rec-2"), exhaustedSuspendedId, "USED", UUID.randomUUID().toString());

        PromotionCloseSummary summary = promotionLifecycleService.closeExpiredAndExhaustedPromotions();

        assertEquals("Finished", promotionStatus(exhaustedActiveId));
        assertEquals("Suspended", promotionStatus(exhaustedSuspendedId));
        assertEquals("USED", couponStatus(cActive));
        assertEquals("USED", couponStatus(cSusp));
        assertEquals(1, summary.exhaustedClosed());
    }

    // ---------------------------------------------------------------- helpers

    private String tokenFor(int userId) {
        User user = User.builder().id(userId).firstName("Owner").email("owner-" + userId + "@example.test").build();
        return jwtService.generateToken(user);
    }

    private String promotionStatus(int discountId) {
        return jdbc.queryForObject(
                "SELECT discount_status::text FROM discounts WHERE discount_id = ?",
                String.class, discountId);
    }

    private String couponStatus(int couponId) {
        return jdbc.queryForObject(
                "SELECT coupon_status::text FROM coupon WHERE coupon_id = ?",
                String.class, couponId);
    }

    private String couponHash(int couponId) {
        return jdbc.queryForObject(
                "SELECT coupon_hash FROM coupon WHERE coupon_id = ?",
                String.class, couponId);
    }

    private Timestamp redemptionTimestamp(int couponId) {
        return jdbc.queryForObject(
                "SELECT coupon_redemption_timestamp FROM coupon WHERE coupon_id = ?",
                Timestamp.class, couponId);
    }

    private int createUser(String name) {
        return jdbc.queryForObject("""
                INSERT INTO users (user_first_name, user_last_name, user_birthday, user_email, user_password_hash)
                VALUES (?, 'Test', DATE '2000-01-01', ?, 'test-only-hash') RETURNING user_id
                """, Integer.class, name, name + "-" + UUID.randomUUID() + "@example.test");
    }

    private int createRestaurant(int ownerId) {
        return jdbc.queryForObject("""
                INSERT INTO restaurants (restaurant_name, restaurant_owner_id, restaurant_full_address)
                VALUES ('Lifecycle restaurant', ?, 'Test address') RETURNING restaurant_id
                """, Integer.class, ownerId);
    }

    private int createPromotionWithEndOffset(int restaurantId, String status, String endOffsetInterval, Integer maxUses) {
        return jdbc.queryForObject("""
                INSERT INTO discounts (discount_restaurant_id, discount_name, discount_status,
                    discount_applicable_products, discount_type, discount_amount,
                    discount_start_timestamp, discount_end_timestamp, discount_max_uses)
                VALUES (?, ?, CAST(? AS discount_status_enum), 'Test menu', 'PERCENTAGE', 20,
                    CURRENT_TIMESTAMP - INTERVAL '2 days',
                    CURRENT_TIMESTAMP + CAST(? AS interval), ?)
                RETURNING discount_id
                """, Integer.class, restaurantId, "Promo-" + UUID.randomUUID(), status, endOffsetInterval, maxUses);
    }

    private int createCoupon(int userId, int discountId, String status, String hash) {
        return jdbc.queryForObject("""
                INSERT INTO coupon (user_id, discount_id, coupon_status, coupon_hash,
                    coupon_claim_timestamp, coupon_redemption_timestamp)
                VALUES (?, ?, CAST(? AS coupon_status_enum), ?, CURRENT_TIMESTAMP - INTERVAL '1 day',
                    CASE WHEN ? = 'USED' THEN CURRENT_TIMESTAMP - INTERVAL '1 hour' ELSE NULL END)
                RETURNING coupon_id
                """, Integer.class, userId, discountId, status, hash, status);
    }
}
