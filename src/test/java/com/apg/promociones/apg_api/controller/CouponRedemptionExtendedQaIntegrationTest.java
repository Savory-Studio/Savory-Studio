package com.apg.promociones.apg_api.controller;

import com.apg.promociones.apg_api.service.PromotionLifecycleService;
import com.apg.promociones.apg_api.util.QrCodeGenerator;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.jayway.jsonpath.JsonPath;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.crypto.SecretKey;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.simulated-auth.enabled=true",
        "app.simulated-auth.token=qa-apg74-fallback",
        "app.promotion-lifecycle.scheduling-enabled=false"
})
@AutoConfigureMockMvc
@Testcontainers
class CouponRedemptionExtendedQaIntegrationTest {

    private static final int CONCURRENT_THREADS = 8;
    private static final Pattern UUID_V4_PATTERN = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

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
    private PromotionLifecycleService promotionLifecycleService;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private record Actor(int userId, String email, String token) {
    }

    private record Outcome(int status, String body) {
    }

    @Test
    void jwtAuthenticatedClaimingAliasParityAntiSpoofingAndConcurrentDuplicateClaimProtection() throws Exception {
        Actor owner = registerAndLogin("claim-owner");
        Actor customer1 = registerAndLogin("claim-cust1");
        Actor customer2 = registerAndLogin("claim-cust2");

        int restaurantId = createRestaurant(owner.token(), "Claim Bistro");
        int activePromoId = createPromotion(owner.token(), restaurantId, "Active Claim Promo", 10, 2);

        // Inactive, future, and expired promotions directly seeded for RN-10, RN-11, RN-12 HTTP 409 checks
        int suspendedPromoId = insertPromoWithWindow(restaurantId, "Suspended Claim", "Suspended", "-1 day", "+5 days");
        int futurePromoId = insertPromoWithWindow(restaurantId, "Future Claim", "Active", "+2 days", "+5 days");
        int expiredPromoId = insertPromoWithWindow(restaurantId, "Expired Claim", "Active", "-5 days", "-1 hour");

        // 401 on missing / tampered / expired / unidentified fallback tokens across /api/coupons and /api/cupones
        for (String route : List.of("/api/coupons", "/api/cupones")) {
            mvc.perform(post(route)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"promotionId\":" + activePromoId + "}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401));

            mvc.perform(post(route)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.jwt.token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"promotionId\":" + activePromoId + "}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401));

            mvc.perform(post(route)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredTokenFor(customer1))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"promotionId\":" + activePromoId + "}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401));

            mvc.perform(post(route)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer qa-apg74-fallback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"promotionId\":" + activePromoId + "}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401));
        }

        // 403 anti-spoofing: mismatched userId in body or spoofed X-User-Id alongside valid JWT
        for (String route : List.of("/api/coupons", "/api/cupones")) {
            mvc.perform(post(route)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + customer1.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"userId\":" + customer2.userId() + ",\"promotionId\":" + activePromoId + "}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.message").value(containsString("otro usuario")));
        }

        mvc.perform(get("/api/cupones/usuario/" + customer2.userId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customer1.token())
                        .header("X-User-Id", String.valueOf(customer2.userId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        // 400 non-positive promotionId & 404 missing promotionId
        mvc.perform(post("/api/coupons")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customer1.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"promotionId\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mvc.perform(post("/api/coupons")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customer1.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"promotionId\":999999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        // 409 RN-10, RN-11, RN-12 checks -> zero coupons created
        assertClaimConflict(customer1.token(), suspendedPromoId, "This promotion is no longer available.");
        assertClaimConflict(customer1.token(), futurePromoId, "This promotion is not active yet.");
        assertClaimConflict(customer1.token(), expiredPromoId, "This promotion has expired.");

        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM coupon WHERE user_id = ?",
                Integer.class, customer1.userId()));

        // Concurrent duplicate claim race (RN-13 + uq_unused_coupon_per_user_promotion): 8 parallel requests -> 1x 201, 7x 409
        List<Outcome> outcomes = fireParallelPosts(
                "/api/cupones",
                customer1.token(),
                java.util.Collections.nCopies(CONCURRENT_THREADS, "{\"promotionId\":" + activePromoId + "}"));

        assertEquals(1, countStatus(outcomes, 201), describe(outcomes));
        assertEquals(CONCURRENT_THREADS - 1, countStatus(outcomes, 409), describe(outcomes));

        Outcome winner = outcomes.stream().filter(o -> o.status() == 201).findFirst().orElseThrow();
        int couponId = JsonPath.read(winner.body(), "$.couponId");
        String hash = JsonPath.read(winner.body(), "$.couponHash");
        assertTrue(UUID_V4_PATTERN.matcher(hash).matches(), "Expected UUID v4 hash, got " + hash);
        assertEquals(customer1.userId(), JsonPath.<Integer>read(winner.body(), "$.userId"));
        assertEquals("UNUSED", JsonPath.read(winner.body(), "$.status"));

        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM coupon WHERE user_id = ? AND discount_id = ?",
                Integer.class, customer1.userId(), activePromoId));
        assertEquals(hash, jdbc.queryForObject(
                "SELECT coupon_hash FROM coupon WHERE coupon_id = ?",
                String.class, couponId));
        assertNull(jdbc.queryForObject(
                "SELECT coupon_redemption_timestamp FROM coupon WHERE coupon_id = ?",
                Timestamp.class, couponId));
    }

    @Test
    void qrCodePngGenerationUuidIntegrityAccessControlAndQrDrivenRedemption() throws Exception {
        Actor owner = registerAndLogin("qr-owner");
        Actor customer1 = registerAndLogin("qr-cust1");
        Actor customer2 = registerAndLogin("qr-cust2");

        int restaurantId = createRestaurant(owner.token(), "QR Bistro");
        int promoId = createPromotion(owner.token(), restaurantId, "QR Promo", 10, 2);
        ClaimedCoupon claimed = claimCoupon(customer1.token(), "/api/coupons", promoId);

        // 200 PNG on both /api/coupons/{id}/qr and /api/cupones/{id}/qr; decode with ZXing
        MvcResult qrPrimary = mvc.perform(get("/api/coupons/" + claimed.couponId() + "/qr")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customer1.token()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andReturn();

        MvcResult qrAlias = mvc.perform(get("/api/cupones/" + claimed.couponId() + "/qr")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customer1.token()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andReturn();

        String decodedPrimary = decodeQrPng(qrPrimary.getResponse().getContentAsByteArray());
        String decodedAlias = decodeQrPng(qrAlias.getResponse().getContentAsByteArray());

        assertEquals(claimed.couponHash(), decodedPrimary);
        assertEquals(claimed.couponHash(), decodedAlias);
        assertTrue(UUID_V4_PATTERN.matcher(decodedPrimary).matches());
        assertFalse(decodedPrimary.contains(customer1.email()));
        assertFalse(decodedPrimary.contains("@"));

        // 401, 403 (other customer & restaurant owner!), 404, 400 on QR endpoint
        mvc.perform(get("/api/coupons/" + claimed.couponId() + "/qr"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/coupons/" + claimed.couponId() + "/qr")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer qa-apg74-fallback"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/coupons/" + claimed.couponId() + "/qr")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customer2.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        mvc.perform(get("/api/coupons/" + claimed.couponId() + "/qr")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        mvc.perform(get("/api/coupons/999999/qr")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customer1.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        mvc.perform(get("/api/coupons/0/qr")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customer1.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mvc.perform(get("/api/coupons/abc/qr")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customer1.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // RN-HASH-03 case sensitivity: uppercase UUID must not match lowercase UUID
        String upperHash = decodedPrimary.toUpperCase();
        if (!upperHash.equals(decodedPrimary)) {
            mvc.perform(post("/api/canjes")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"couponHash\":\"" + upperHash + "\"}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }

        assertEquals("UNUSED", jdbc.queryForObject(
                "SELECT coupon_status::text FROM coupon WHERE coupon_id = ?",
                String.class, claimed.couponId()));

        // PH-05 hash priority over non-existent couponId, redeeming from decoded QR PNG
        mvc.perform(post("/api/canjes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"couponId\":999999,\"couponHash\":\"" + decodedPrimary + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.couponId").value(claimed.couponId()))
                .andExpect(jsonPath("$.couponHash").value(claimed.couponHash()))
                .andExpect(jsonPath("$.status").value("USED"));

        assertEquals("USED", jdbc.queryForObject(
                "SELECT coupon_status::text FROM coupon WHERE coupon_id = ?",
                String.class, claimed.couponId()));
        assertEquals(claimed.couponHash(), jdbc.queryForObject(
                "SELECT coupon_hash FROM coupon WHERE coupon_id = ?",
                String.class, claimed.couponId()));
        assertNotNull(jdbc.queryForObject(
                "SELECT coupon_redemption_timestamp FROM coupon WHERE coupon_id = ?",
                Timestamp.class, claimed.couponId()));
    }

    @Test
    void restaurantStaffRedemptionPermissionsCrossRestaurantIsolationAndImmediateRevocation() throws Exception {
        Actor ownerA = registerAndLogin("redeem-owner-a");
        Actor ownerB = registerAndLogin("redeem-owner-b");
        Actor staffA = registerAndLogin("redeem-staff-a");
        Actor outsider = registerAndLogin("redeem-outsider");
        Actor customer = registerAndLogin("redeem-customer");

        int restaurantAId = createRestaurant(ownerA.token(), "Resto Auth A");
        int restaurantBId = createRestaurant(ownerB.token(), "Resto Auth B");

        int promoAId = createPromotion(ownerA.token(), restaurantAId, "Promo Auth A", 10, 5);
        int promoBId = createPromotion(ownerB.token(), restaurantBId, "Promo Auth B", 10, 5);

        ClaimedCoupon couponA1 = claimCoupon(customer.token(), "/api/coupons", promoAId);
        ClaimedCoupon couponB = claimCoupon(customer.token(), "/api/coupons", promoBId);

        // Link staffA exclusively to restaurantA
        linkStaff(ownerA.token(), restaurantAId, staffA.email());

        // 403 unauthorized callers: customer self-redeem, foreign ownerB, outsider, and staffA against restaurantB
        for (Actor deniedCaller : List.of(customer, ownerB, outsider)) {
            mvc.perform(post("/api/canjes")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + deniedCaller.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"couponHash\":\"" + couponA1.couponHash() + "\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
        }

        mvc.perform(post("/api/canjes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + staffA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"couponHash\":\"" + couponB.couponHash() + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        // 401 unidentified fallback & 400 blank couponHash
        mvc.perform(post("/api/canjes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer qa-apg74-fallback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"couponHash\":\"" + couponA1.couponHash() + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mvc.perform(post("/api/canjes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + staffA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"couponHash\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        assertEquals("UNUSED", couponStatus(couponA1.couponId()));
        assertEquals("UNUSED", couponStatus(couponB.couponId()));

        // CUP-04 positive: linked staffA redeems couponA1 by QR hash -> 201 Created
        mvc.perform(post("/api/canjes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + staffA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"couponHash\":\"" + couponA1.couponHash() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("USED"));
        assertEquals("USED", couponStatus(couponA1.couponId()));

        // Customer claims a second coupon on promoA, ownerA unlinks staffA (204), staffA immediately gets 403
        ClaimedCoupon couponA2 = claimCoupon(customer.token(), "/api/cupones", promoAId);

        mvc.perform(delete("/api/restaurants/" + restaurantAId + "/staff/" + staffA.userId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerA.token()))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/canjes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + staffA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"couponHash\":\"" + couponA2.couponHash() + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        assertEquals("UNUSED", couponStatus(couponA2.couponId()));
        assertNull(redemptionTimestamp(couponA2.couponId()));
    }

    @Test
    void concurrentSameCouponMixedActorsGlobalMaxUsesAndPerUserReclaimLifecycle() throws Exception {
        Actor owner = registerAndLogin("race-owner");
        Actor staff = registerAndLogin("race-staff");
        int restaurantId = createRestaurant(owner.token(), "Race Restaurant");
        linkStaff(owner.token(), restaurantId, staff.email());

        // 1. Mixed Owner (4 threads) + Linked Staff (4 threads) racing on the exact same QR coupon -> 1x 201, 7x 409
        int singlePromoId = createPromotion(owner.token(), restaurantId, "Mixed Race Promo", 10, 2);
        Actor mixedCustomer = registerAndLogin("race-cust-mixed");
        ClaimedCoupon mixedCoupon = claimCoupon(mixedCustomer.token(), "/api/coupons", singlePromoId);

        List<String> actorTokens = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            actorTokens.add(owner.token());
            actorTokens.add(staff.token());
        }
        List<Outcome> mixedOutcomes = fireParallelPostsWithTokens(
                "/api/canjes",
                actorTokens,
                java.util.Collections.nCopies(CONCURRENT_THREADS, "{\"couponHash\":\"" + mixedCoupon.couponHash() + "\"}"));

        assertEquals(1, countStatus(mixedOutcomes, 201), describe(mixedOutcomes));
        assertEquals(CONCURRENT_THREADS - 1, countStatus(mixedOutcomes, 409), describe(mixedOutcomes));
        Outcome winner = mixedOutcomes.stream().filter(o -> o.status() == 201).findFirst().orElseThrow();
        LocalDateTime returnedTs = LocalDateTime.parse(JsonPath.read(winner.body(), "$.redemptionTimestamp"));
        LocalDateTime persistedTs = redemptionTimestamp(mixedCoupon.couponId()).toLocalDateTime();
        assertTrue(Duration.between(returnedTs, persistedTs).abs().toMillis() < 1);
        assertEquals("USED", couponStatus(mixedCoupon.couponId()));

        // 2. Global limit race (RN-05 + CUP-06): maxUses = 2, 6 customers claim & staff redeems concurrently -> 2x 201, 4x 409
        int maxUses = 2;
        int globalPromoId = createPromotion(owner.token(), restaurantId, "Global Limit Promo", maxUses, 1);
        List<String> payloads = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Actor c = registerAndLogin("race-global-" + i);
            ClaimedCoupon cc = claimCoupon(c.token(), "/api/coupons", globalPromoId);
            payloads.add("{\"couponHash\":\"" + cc.couponHash() + "\"}");
        }
        List<Outcome> globalOutcomes = fireParallelPosts("/api/canjes", staff.token(), payloads);

        assertEquals(maxUses, countStatus(globalOutcomes, 201), describe(globalOutcomes));
        assertEquals(6 - maxUses, countStatus(globalOutcomes, 409), describe(globalOutcomes));
        globalOutcomes.stream().filter(o -> o.status() == 409)
                .forEach(o -> assertTrue(o.body().contains("agotado"), o.body()));

        assertEquals(maxUses, jdbc.queryForObject(
                "SELECT COUNT(*) FROM coupon WHERE discount_id = ? AND coupon_status = 'USED'",
                Integer.class, globalPromoId));
        assertEquals(4, jdbc.queryForObject(
                "SELECT COUNT(*) FROM coupon WHERE discount_id = ? AND coupon_status = 'UNUSED' AND coupon_redemption_timestamp IS NULL",
                Integer.class, globalPromoId));
        assertEquals("Finished", promotionStatus(globalPromoId));

        Actor lateCustomer = registerAndLogin("race-late");
        assertClaimConflict(lateCustomer.token(), globalPromoId, "This promotion is no longer available.");

        // 3. Per-user limit across claim -> redeem -> re-claim cycles (RN-04 + CUP-01 history retention)
        int perUserPromoId = createPromotion(owner.token(), restaurantId, "Per User Lifecycle Promo", 10, 2);
        Actor repeatCustomer = registerAndLogin("repeat-cust");

        ClaimedCoupon c1 = claimCoupon(repeatCustomer.token(), "/api/coupons", perUserPromoId);
        redeemSuccess(staff.token(), c1.couponHash());

        ClaimedCoupon c2 = claimCoupon(repeatCustomer.token(), "/api/coupons", perUserPromoId);
        redeemSuccess(staff.token(), c2.couponHash());

        // RN-13 allows reclaiming when no UNUSED coupon exists; RN-04 then blocks the 3rd redemption with 409
        ClaimedCoupon c3 = claimCoupon(repeatCustomer.token(), "/api/coupons", perUserPromoId);
        mvc.perform(post("/api/canjes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + staff.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"couponHash\":\"" + c3.couponHash() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Has alcanzado el límite de usos de esta promoción."));

        assertEquals("UNUSED", couponStatus(c3.couponId()));
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM coupon WHERE user_id = ? AND discount_id = ? AND coupon_status = 'USED'",
                Integer.class, repeatCustomer.userId(), perUserPromoId));

        // GET /api/cupones/usuario/{id} retains full history (2 USED + 1 UNUSED = 3 rows)
        mvc.perform(get("/api/cupones/usuario/" + repeatCustomer.userId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + repeatCustomer.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void expirationTransitionsRejectClaimsAndRedemptionsWithoutMutatingCouponHistory() throws Exception {
        Actor owner = registerAndLogin("exp-owner");
        Actor custUnused = registerAndLogin("exp-cust-unused");
        Actor custUsed = registerAndLogin("exp-cust-used");
        Actor custLate = registerAndLogin("exp-cust-late");

        int restaurantId = createRestaurant(owner.token(), "Expiration Restaurant");
        int activeExpPromoId = createPromotion(owner.token(), restaurantId, "Expiring Active Promo", 20, 5);
        int suspendedExpPromoId = insertPromoWithWindow(restaurantId, "Expiring Suspended", "Suspended", "-3 days", "-1 hour");

        ClaimedCoupon unusedCoupon = claimCoupon(custUnused.token(), "/api/coupons", activeExpPromoId);
        ClaimedCoupon usedCoupon = claimCoupon(custUsed.token(), "/api/coupons", activeExpPromoId);
        redeemSuccess(owner.token(), usedCoupon.couponHash());
        Timestamp originalUsedTimestamp = redemptionTimestamp(usedCoupon.couponId());
        assertNotNull(originalUsedTimestamp);

        // Simulate time passing beyond discount_end_timestamp before the periodic closure job runs
        jdbc.update("""
                UPDATE discounts
                SET discount_end_timestamp = CURRENT_TIMESTAMP - INTERVAL '2 minutes'
                WHERE discount_id = ?
                """, activeExpPromoId);
        assertEquals("Active", promotionStatus(activeExpPromoId));

        // Before closure: RN-12 blocks new claim, RN-03 blocks redemption of UNUSED coupon
        assertClaimConflict(custLate.token(), activeExpPromoId, "This promotion has expired.");
        mvc.perform(post("/api/canjes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"couponHash\":\"" + unusedCoupon.couponHash() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Esta promoción ha vencido."));
        assertEquals("UNUSED", couponStatus(unusedCoupon.couponId()));

        // Run lifecycle closure: Active -> Finished, Suspended untouched, coupon history preserved
        promotionLifecycleService.closeExpiredAndExhaustedPromotions();
        assertEquals("Finished", promotionStatus(activeExpPromoId));
        assertEquals("Suspended", promotionStatus(suspendedExpPromoId));
        assertEquals("UNUSED", couponStatus(unusedCoupon.couponId()));
        assertNull(redemptionTimestamp(unusedCoupon.couponId()));
        assertEquals("USED", couponStatus(usedCoupon.couponId()));
        assertEquals(originalUsedTimestamp, redemptionTimestamp(usedCoupon.couponId()));

        // After closure: RN-10 blocks claim, RN-02 blocks redemption -> zero side effects
        assertClaimConflict(custLate.token(), activeExpPromoId, "This promotion is no longer available.");
        mvc.perform(post("/api/canjes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"couponHash\":\"" + unusedCoupon.couponHash() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Esta promoción ya no está disponible."));
        assertEquals("UNUSED", couponStatus(unusedCoupon.couponId()));
        assertNull(redemptionTimestamp(unusedCoupon.couponId()));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM coupon WHERE user_id = ?",
                Integer.class, custLate.userId()));
    }

    // ---------------------------------------------------------------- helpers

    private Actor registerAndLogin(String prefix) throws Exception {
        String email = prefix + "-" + UUID.randomUUID() + "@example.test";
        String password = "SecureP@ss123!";
        MvcResult reg = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "QA",
                                  "lastName": "Tester",
                                  "birthday": "1997-04-12",
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isCreated())
                .andReturn();
        int userId = JsonPath.read(reg.getResponse().getContentAsString(), "$.id");

        MvcResult login = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String token = JsonPath.read(login.getResponse().getContentAsString(), "$.token");
        return new Actor(userId, email, token);
    }

    private int createRestaurant(String ownerToken, String name) throws Exception {
        MvcResult res = mvc.perform(post("/api/restaurantes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","fullAddress":"Calle QA #10-20"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(res.getResponse().getContentAsString(), "$.restaurantId");
    }

    private int createPromotion(String ownerToken, int restaurantId, String name, int maxUses, int maxPerUser)
            throws Exception {
        MvcResult res = mvc.perform(post("/api/promociones")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "restaurantId": %d,
                                  "name": "%s",
                                  "applicableProducts": "Menu ejecutivo",
                                  "type": "PERCENTAGE",
                                  "amount": 25.00,
                                  "startTimestamp": "%s",
                                  "endTimestamp": "%s",
                                  "maxUses": %d,
                                  "maxUseAmountPerUser": %d
                                }
                                """.formatted(
                                restaurantId,
                                name,
                                LocalDateTime.now().minusDays(1),
                                LocalDateTime.now().plusDays(7),
                                maxUses,
                                maxPerUser)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(res.getResponse().getContentAsString(), "$.discountId");
    }

    private int insertPromoWithWindow(
            int restaurantId, String name, String status, String startOffset, String endOffset) {
        return jdbc.queryForObject("""
                INSERT INTO discounts (discount_restaurant_id, discount_name, discount_status,
                    discount_applicable_products, discount_type, discount_amount,
                    discount_start_timestamp, discount_end_timestamp, discount_max_uses)
                VALUES (?, ?, CAST(? AS discount_status_enum), 'Menu QA', 'PERCENTAGE', 15,
                    CURRENT_TIMESTAMP + CAST(? AS interval),
                    CURRENT_TIMESTAMP + CAST(? AS interval), 50)
                RETURNING discount_id
                """, Integer.class, restaurantId, name + "-" + UUID.randomUUID(), status, startOffset, endOffset);
    }

    private void linkStaff(String ownerToken, int restaurantId, String staffEmail) throws Exception {
        mvc.perform(post("/api/restaurants/" + restaurantId + "/staff")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + staffEmail + "\"}"))
                .andExpect(status().isCreated());
    }

    private ClaimedCoupon claimCoupon(String customerToken, String route, int promotionId) throws Exception {
        MvcResult res = mvc.perform(post(route)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"promotionId\":" + promotionId + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        String json = res.getResponse().getContentAsString();
        return new ClaimedCoupon(JsonPath.read(json, "$.couponId"), JsonPath.read(json, "$.couponHash"));
    }

    private void assertClaimConflict(String customerToken, int promotionId, String expectedMessage) throws Exception {
        mvc.perform(post("/api/coupons")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"promotionId\":" + promotionId + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(expectedMessage));
    }

    private void redeemSuccess(String actorToken, String couponHash) throws Exception {
        mvc.perform(post("/api/canjes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + actorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"couponHash\":\"" + couponHash + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("USED"));
    }

    private static String decodeQrPng(byte[] pngBytes) throws Exception {
        byte[] header = new byte[PNG_SIGNATURE.length];
        System.arraycopy(pngBytes, 0, header, 0, PNG_SIGNATURE.length);
        assertArrayEquals(PNG_SIGNATURE, header);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(pngBytes));
        assertNotNull(image);
        assertEquals(QrCodeGenerator.DEFAULT_SIZE, image.getWidth());
        assertEquals(QrCodeGenerator.DEFAULT_SIZE, image.getHeight());

        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
        return new QRCodeReader().decode(bitmap).getText();
    }

    private List<Outcome> fireParallelPosts(String uri, String token, List<String> payloads) throws Exception {
        return fireParallelPostsWithTokens(uri, java.util.Collections.nCopies(payloads.size(), token), payloads);
    }

    private List<Outcome> fireParallelPostsWithTokens(String uri, List<String> tokens, List<String> payloads)
            throws Exception {
        CountDownLatch ready = new CountDownLatch(payloads.size());
        CountDownLatch go = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(payloads.size())) {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (int i = 0; i < payloads.size(); i++) {
                final String token = tokens.get(i);
                final String payload = payloads.get(i);
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    go.await();
                    MockHttpServletResponse response = mvc.perform(post(uri)
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

    private String expiredTokenFor(Actor actor) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(actor.userId()))
                .claim("email", actor.email())
                .issuedAt(new Date(now.getTime() - 120_000L))
                .expiration(new Date(now.getTime() - 60_000L))
                .signWith(key)
                .compact();
    }

    private String couponStatus(int couponId) {
        return jdbc.queryForObject(
                "SELECT coupon_status::text FROM coupon WHERE coupon_id = ?",
                String.class, couponId);
    }

    private Timestamp redemptionTimestamp(int couponId) {
        return jdbc.queryForObject(
                "SELECT coupon_redemption_timestamp FROM coupon WHERE coupon_id = ?",
                Timestamp.class, couponId);
    }

    private String promotionStatus(int discountId) {
        return jdbc.queryForObject(
                "SELECT discount_status::text FROM discounts WHERE discount_id = ?",
                String.class, discountId);
    }

    private static long countStatus(List<Outcome> outcomes, int status) {
        return outcomes.stream().filter(o -> o.status() == status).count();
    }

    private static String describe(List<Outcome> outcomes) {
        return outcomes.stream().map(o -> o.status() + " " + o.body()).toList().toString();
    }

    private record ClaimedCoupon(int couponId, String couponHash) {
    }
}
