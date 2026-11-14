package com.apg.promociones.apg_api.controller;

import com.apg.promociones.apg_api.model.User;
import com.apg.promociones.apg_api.service.LoginServices.JwtService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * APG-65: simultaneous redemptions against a real PostgreSQL instance, going through the real HTTP stack
 * (JWT filter, controller, service, row locks). All requests of a scenario are released at the same instant
 * with a {@link CountDownLatch} so they genuinely race.
 */
@SpringBootTest(properties = "app.simulated-auth.enabled=true")
@AutoConfigureMockMvc
@Testcontainers
class CanjeConcurrencyIntegrationTest {

    /** Kept below the default Hikari pool size (10): every waiting request holds one connection. */
    private static final int CONCURRENT_REQUESTS = 8;

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

    private record Outcome(int status, String body) {
    }

    @Test
    void sameCouponRedeemedSimultaneouslySucceedsOnceAndTheRestGet409() throws Exception {
        int ownerId = createUser("owner-same-coupon");
        int restaurantId = createRestaurant(ownerId);
        int discountId = createDiscount(restaurantId, null, null);
        int customerId = createUser("customer-same-coupon");
        int couponId = createCoupon(customerId, discountId);

        List<Outcome> outcomes = fire(tokenFor(ownerId),
                repeat("{\"couponId\":" + couponId + "}", CONCURRENT_REQUESTS));

        assertEquals(1, count(outcomes, 201), describe(outcomes));
        assertEquals(CONCURRENT_REQUESTS - 1, count(outcomes, 409), describe(outcomes));
        outcomes.stream().filter(o -> o.status() == 409).forEach(o ->
                assertTrue(o.body().contains("Este cupón ya fue utilizado."), o.body()));

        assertEquals("USED", couponStatus(couponId));
        assertEquals(1, usedCount("WHERE coupon_id = ?", couponId));

        // No lost update: the persisted redemption timestamp is the one returned to the winner.
        Outcome winner = outcomes.stream().filter(o -> o.status() == 201).findFirst().orElseThrow();
        LocalDateTime returned = LocalDateTime.parse(JsonPath.read(winner.body(), "$.redemptionTimestamp"));
        LocalDateTime persisted = redemptionTimestamp(couponId);
        assertNotNull(persisted);
        assertTrue(Duration.between(returned, persisted).abs().toMillis() < 1,
                "persisted=" + persisted + " returned=" + returned);
    }

    @Test
    void sameCouponRedeemedSimultaneouslyByHashSucceedsOnce() throws Exception {
        int ownerId = createUser("owner-hash");
        int restaurantId = createRestaurant(ownerId);
        int discountId = createDiscount(restaurantId, null, null);
        int customerId = createUser("customer-hash");
        int couponId = createCoupon(customerId, discountId);
        String hash = jdbc.queryForObject("SELECT coupon_hash FROM coupon WHERE coupon_id = ?", String.class, couponId);

        List<Outcome> outcomes = fire(tokenFor(ownerId),
                repeat("{\"couponHash\":\"" + hash + "\"}", CONCURRENT_REQUESTS));

        assertEquals(1, count(outcomes, 201), describe(outcomes));
        assertEquals(CONCURRENT_REQUESTS - 1, count(outcomes, 409), describe(outcomes));
        assertEquals(1, usedCount("WHERE coupon_id = ?", couponId));
    }

    @Test
    void differentCouponsNeverExceedTheGlobalLimit() throws Exception {
        int maxUses = 3;
        int ownerId = createUser("owner-global");
        int restaurantId = createRestaurant(ownerId);
        int discountId = createDiscount(restaurantId, maxUses, null);
        List<Integer> couponIds = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            couponIds.add(createCoupon(createUser("customer-global-" + i), discountId));
        }

        List<Outcome> outcomes = fire(tokenFor(ownerId),
                couponIds.stream().map(id -> "{\"couponId\":" + id + "}").toList());

        assertEquals(maxUses, count(outcomes, 201), describe(outcomes));
        assertEquals(CONCURRENT_REQUESTS - maxUses, count(outcomes, 409), describe(outcomes));
        outcomes.stream().filter(o -> o.status() == 409).forEach(o ->
                assertTrue(o.body().contains("Esta promoción se ha agotado."), o.body()));

        assertEquals(maxUses, usedCount("WHERE discount_id = ?", discountId));
        // Rejected requests left their coupons untouched.
        assertEquals(CONCURRENT_REQUESTS - maxUses, jdbc.queryForObject("""
                SELECT COUNT(*) FROM coupon
                WHERE discount_id = ? AND coupon_status = 'UNUSED' AND coupon_redemption_timestamp IS NULL
                """, Integer.class, discountId));
    }

    @Test
    void sameUserNeverExceedsThePerUserLimitEvenWithSeveralUnusedCoupons() throws Exception {
        int perUserLimit = 2;
        int ownerId = createUser("owner-per-user");
        int restaurantId = createRestaurant(ownerId);
        int discountId = createDiscount(restaurantId, null, perUserLimit);
        int customerId = createUser("customer-per-user");

        // The partial unique index normally keeps a single UNUSED coupon per user and promotion, which would
        // hide this race. It is dropped only inside this test to prove that the service-level lock alone
        // (defense in depth) enforces RN-04, and it is always restored afterwards.
        jdbc.execute("DROP INDEX uq_unused_coupon_per_user_promotion");
        try {
            List<Integer> couponIds = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                couponIds.add(createCoupon(customerId, discountId));
            }

            List<Outcome> outcomes = fire(tokenFor(ownerId),
                    couponIds.stream().map(id -> "{\"couponId\":" + id + "}").toList());

            assertEquals(perUserLimit, count(outcomes, 201), describe(outcomes));
            assertEquals(couponIds.size() - perUserLimit, count(outcomes, 409), describe(outcomes));
            outcomes.stream().filter(o -> o.status() == 409).forEach(o ->
                    assertTrue(o.body().contains("Has alcanzado el límite de usos de esta promoción."), o.body()));
            assertEquals(perUserLimit, usedCount("WHERE discount_id = ? AND user_id = " + customerId, discountId));
        } finally {
            jdbc.update("DELETE FROM coupon WHERE user_id = ? AND discount_id = ? AND coupon_status = 'UNUSED' "
                    + "AND coupon_id NOT IN (SELECT MIN(coupon_id) FROM coupon WHERE user_id = ? AND discount_id = ? "
                    + "AND coupon_status = 'UNUSED')", customerId, discountId, customerId, discountId);
            jdbc.execute("CREATE UNIQUE INDEX uq_unused_coupon_per_user_promotion "
                    + "ON coupon (user_id, discount_id) WHERE coupon_status = 'UNUSED'");
        }
    }

    @Test
    void rejectedRedemptionLeavesTheOriginalUsedCouponUntouched() throws Exception {
        int ownerId = createUser("owner-untouched");
        int restaurantId = createRestaurant(ownerId);
        int discountId = createDiscount(restaurantId, null, null);
        int customerId = createUser("customer-untouched");
        int couponId = createCoupon(customerId, discountId);
        String token = tokenFor(ownerId);

        List<Outcome> first = fire(token, List.of("{\"couponId\":" + couponId + "}"));
        assertEquals(201, first.get(0).status());
        LocalDateTime original = redemptionTimestamp(couponId);

        List<Outcome> retries = fire(token, repeat("{\"couponId\":" + couponId + "}", CONCURRENT_REQUESTS));

        assertEquals(CONCURRENT_REQUESTS, count(retries, 409), describe(retries));
        assertEquals(original, redemptionTimestamp(couponId));
        assertEquals(1, usedCount("WHERE coupon_id = ?", couponId));
    }

    // ---------------------------------------------------------------- helpers

    private List<Outcome> fire(String token, List<String> payloads) throws Exception {
        CountDownLatch ready = new CountDownLatch(payloads.size());
        CountDownLatch go = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(payloads.size())) {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (String payload : payloads) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    go.await();
                    MockHttpServletResponse response = mvc.perform(post("/api/canjes")
                                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(payload))
                            .andReturn().getResponse();
                    return new Outcome(response.getStatus(), response.getContentAsString(StandardCharsets.UTF_8));
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS), "all request threads should be ready");
            go.countDown();
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                outcomes.add(future.get(60, TimeUnit.SECONDS));
            }
            return outcomes;
        }
    }

    private static List<String> repeat(String payload, int times) {
        return java.util.Collections.nCopies(times, payload);
    }

    private static long count(List<Outcome> outcomes, int status) {
        return outcomes.stream().filter(o -> o.status() == status).count();
    }

    private static String describe(List<Outcome> outcomes) {
        return outcomes.stream().map(o -> o.status() + " " + o.body()).toList().toString();
    }

    private String tokenFor(int userId) {
        User user = User.builder().id(userId).firstName("Owner").email("owner-" + userId + "@example.test").build();
        return jwtService.generateToken(user);
    }

    private int usedCount(String whereClause, Object... args) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM coupon " + whereClause + " AND coupon_status = 'USED'",
                Integer.class, args);
    }

    private String couponStatus(int couponId) {
        return jdbc.queryForObject("SELECT coupon_status::text FROM coupon WHERE coupon_id = ?", String.class, couponId);
    }

    private LocalDateTime redemptionTimestamp(int couponId) {
        Timestamp value = jdbc.queryForObject(
                "SELECT coupon_redemption_timestamp FROM coupon WHERE coupon_id = ?", Timestamp.class, couponId);
        return value == null ? null : value.toLocalDateTime();
    }

    private int createUser(String name) {
        return jdbc.queryForObject("""
                INSERT INTO users (user_first_name, user_last_name, user_birthday, user_email, user_password_hash)
                VALUES (?, 'Test', DATE '2000-01-01', ?, 'test-only-hash') RETURNING user_id
                """, Integer.class, name, name + "@example.test");
    }

    private int createRestaurant(int ownerId) {
        return jdbc.queryForObject("""
                INSERT INTO restaurants (restaurant_name, restaurant_owner_id, restaurant_full_address)
                VALUES ('Concurrency restaurant', ?, 'Test address') RETURNING restaurant_id
                """, Integer.class, ownerId);
    }

    private int createDiscount(int restaurantId, Integer maxUses, Integer maxUsesPerUser) {
        return jdbc.queryForObject("""
                INSERT INTO discounts (discount_restaurant_id, discount_name, discount_status,
                    discount_applicable_products, discount_type, discount_amount,
                    discount_start_timestamp, discount_end_timestamp, discount_max_uses,
                    discount_max_use_amount_per_user)
                VALUES (?, 'Concurrency promotion', 'Active', 'Test menu', 'PERCENTAGE', 20,
                    CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP + INTERVAL '2 days', ?, ?)
                RETURNING discount_id
                """, Integer.class, restaurantId, maxUses, maxUsesPerUser);
    }

    private int createCoupon(int userId, int discountId) {
        return jdbc.queryForObject("""
                INSERT INTO coupon (user_id, discount_id, coupon_status, coupon_hash, coupon_claim_timestamp)
                VALUES (?, ?, 'UNUSED', ?, CURRENT_TIMESTAMP - INTERVAL '1 day')
                RETURNING coupon_id
                """, Integer.class, userId, discountId, UUID.randomUUID().toString());
    }
}
