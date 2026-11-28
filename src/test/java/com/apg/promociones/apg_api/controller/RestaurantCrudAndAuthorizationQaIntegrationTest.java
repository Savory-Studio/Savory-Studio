package com.apg.promociones.apg_api.controller;

import com.jayway.jsonpath.JsonPath;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.simulated-auth.enabled=true",
        "app.simulated-auth.token=qa-fallback-token",
        "app.promotion-lifecycle.scheduling-enabled=false"
})
@AutoConfigureMockMvc
@Testcontainers
class RestaurantCrudAndAuthorizationQaIntegrationTest {

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

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Test
    void fullRestaurantCrudLifecyclePersistsExpectedRowsAndEnforcesValidation() throws Exception {
        Actor ownerA = registerAndLogin("owner-a");
        Actor ownerB = registerAndLogin("owner-b");
        Actor outsider = registerAndLogin("outsider-crud");

        // 401 checks on POST /api/restaurantes
        mvc.perform(post("/api/restaurantes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Sin Token","fullAddress":"Calle 1"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mvc.perform(post("/api/restaurantes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.jwt.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Token Invalido","fullAddress":"Calle 1"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mvc.perform(post("/api/restaurantes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredTokenFor(ownerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Token Expirado","fullAddress":"Calle 1"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mvc.perform(post("/api/restaurantes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer qa-fallback-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Sin UserId","fullAddress":"Calle 1"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        // 400 validation checks on POST /api/restaurantes
        List<String> invalidCreatePayloads = List.of(
                "{}",
                "{",
                "{\"fullAddress\":\"Calle 10 #20-30\"}",
                "{\"name\":\"   \",\"fullAddress\":\"Calle 10 #20-30\"}",
                "{\"name\":\"Restaurante\",\"fullAddress\":\"\"}",
                "{\"name\":\"" + "N".repeat(256) + "\",\"fullAddress\":\"Calle 10\"}",
                "{\"name\":\"Restaurante\",\"fullAddress\":\"" + "A".repeat(256) + "\"}"
        );
        for (String payload : invalidCreatePayloads) {
            mvc.perform(post("/api/restaurantes")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerA.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }

        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM restaurants WHERE restaurant_owner_id = ?",
                Integer.class, ownerA.userId()));

        // 201 create restaurant #1 with trimmed inputs and ignored ownerId/X-User-Id spoofing
        int restaurant1Id = createRestaurant(
                ownerA.token(),
                """
                {
                  "name": "  La Parrilla de OwnerA  ",
                  "description": "  Asados al carbón  ",
                  "fullAddress": "  Calle 45 #12-30  ",
                  "ownerId": %d
                }
                """.formatted(ownerB.userId()),
                ownerB.userId()
        );

        Map<String, Object> createdRow1 = jdbc.queryForMap(
                "SELECT restaurant_name, restaurant_owner_id, restaurant_description, restaurant_full_address, restaurant_creation_date "
                        + "FROM restaurants WHERE restaurant_id = ?",
                restaurant1Id);
        assertEquals("La Parrilla de OwnerA", createdRow1.get("restaurant_name"));
        assertEquals(ownerA.userId(), createdRow1.get("restaurant_owner_id"));
        assertEquals("Asados al carbón", createdRow1.get("restaurant_description"));
        assertEquals("Calle 45 #12-30", createdRow1.get("restaurant_full_address"));
        Timestamp originalCreatedAt = (Timestamp) createdRow1.get("restaurant_creation_date");
        assertNotNull(originalCreatedAt);

        // Ensure strict timestamp ordering for GET /api/restaurantes/mios
        Thread.sleep(10L);

        // 201 create restaurant #2 for ownerA without description and restaurant #3 for ownerB
        int restaurant2Id = createRestaurant(
                ownerA.token(),
                """
                {
                  "name": "Café Norte",
                  "fullAddress": "Carrera 15 #40-10"
                }
                """,
                null
        );
        assertNull(jdbc.queryForObject(
                "SELECT restaurant_description FROM restaurants WHERE restaurant_id = ?",
                String.class, restaurant2Id));

        int restaurantBId = createRestaurant(
                ownerB.token(),
                """
                {
                  "name": "Sushi OwnerB",
                  "description": "Cocina japonesa",
                  "fullAddress": "Avenida 33 #48-15"
                }
                """,
                null
        );

        // GET /api/restaurantes/mios (401 + 200 ordered newest first + empty for outsider)
        mvc.perform(get("/api/restaurantes/mios"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mvc.perform(get("/api/restaurantes/mios")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].restaurantId").value(restaurant2Id))
                .andExpect(jsonPath("$[0].name").value("Café Norte"))
                .andExpect(jsonPath("$[1].restaurantId").value(restaurant1Id))
                .andExpect(jsonPath("$[1].ownerId").value(ownerA.userId()));

        mvc.perform(get("/api/restaurantes/mios")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerB.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].restaurantId").value(restaurantBId));

        mvc.perform(get("/api/restaurantes/mios")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + outsider.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // GET /api/restaurantes/{id} (200, 400, 401, 404)
        mvc.perform(get("/api/restaurantes/" + restaurant1Id))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mvc.perform(get("/api/restaurantes/" + restaurant1Id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantId").value(restaurant1Id))
                .andExpect(jsonPath("$.name").value("La Parrilla de OwnerA"))
                .andExpect(jsonPath("$.ownerId").value(ownerA.userId()))
                .andExpect(jsonPath("$.description").value("Asados al carbón"))
                .andExpect(jsonPath("$.fullAddress").value("Calle 45 #12-30"));

        mvc.perform(get("/api/restaurantes/0")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerA.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mvc.perform(get("/api/restaurantes/abc")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerA.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mvc.perform(get("/api/restaurantes/999999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerA.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        // PATCH /api/restaurantes/{id}: full update, partial clear, invalid (400), 401, 404
        mvc.perform(patch("/api/restaurantes/" + restaurant1Id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Sin Token"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mvc.perform(patch("/api/restaurantes/" + restaurant1Id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "  La Parrilla Gourmet  ",
                                  "description": "  Parrilla de autor  ",
                                  "fullAddress": "  Carrera 27 #34-10  "
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantId").value(restaurant1Id))
                .andExpect(jsonPath("$.name").value("La Parrilla Gourmet"))
                .andExpect(jsonPath("$.ownerId").value(ownerA.userId()))
                .andExpect(jsonPath("$.description").value("Parrilla de autor"))
                .andExpect(jsonPath("$.fullAddress").value("Carrera 27 #34-10"))
                .andExpect(jsonPath("$.message").value("Restaurante actualizado exitosamente."));

        mvc.perform(patch("/api/restaurantes/" + restaurant1Id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":null}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("La Parrilla Gourmet"))
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.fullAddress").value("Carrera 27 #34-10"));

        List<String> invalidUpdatePayloads = List.of(
                "{}",
                "{\"name\":\"   \"}",
                "{\"fullAddress\":\"\"}",
                "{\"name\":\"" + "X".repeat(256) + "\"}",
                "{\"name\":\"Attempt\",\"ownerId\":" + ownerB.userId() + "}",
                "{\"restaurantOwnerId\":" + ownerB.userId() + "}"
        );
        for (String payload : invalidUpdatePayloads) {
            mvc.perform(patch("/api/restaurantes/" + restaurant1Id)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerA.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }

        mvc.perform(patch("/api/restaurantes/0")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Valido\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mvc.perform(patch("/api/restaurantes/999999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Inexistente\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        // Verify persisted SQL row retains valid updated fields and immutable owner & creation timestamp
        Map<String, Object> updatedRow = jdbc.queryForMap(
                "SELECT restaurant_name, restaurant_owner_id, restaurant_description, restaurant_full_address, restaurant_creation_date "
                        + "FROM restaurants WHERE restaurant_id = ?",
                restaurant1Id);
        assertEquals("La Parrilla Gourmet", updatedRow.get("restaurant_name"));
        assertEquals(ownerA.userId(), updatedRow.get("restaurant_owner_id"));
        assertNull(updatedRow.get("restaurant_description"));
        assertEquals("Carrera 27 #34-10", updatedRow.get("restaurant_full_address"));
        assertEquals(originalCreatedAt, updatedRow.get("restaurant_creation_date"));
    }

    @Test
    void staffLifecycleAndDatabaseConstraintsEnforceUniquenessAndRestrictDeletes() throws Exception {
        Actor ownerA = registerAndLogin("staff-owner");
        Actor staffA = registerAndLogin("staff-member");
        int restaurantAId = createRestaurant(ownerA.token(), """
                {"name":"Bistro Staff","fullAddress":"Calle 10 #5-20"}
                """, null);

        // 401 without token across POST / GET / DELETE staff endpoints
        mvc.perform(post("/api/restaurants/" + restaurantAId + "/staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(staffA.email())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mvc.perform(get("/api/restaurants/" + restaurantAId + "/staff"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mvc.perform(delete("/api/restaurants/" + restaurantAId + "/staff/" + staffA.userId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        // 400 validation & self-assignment checks
        mvc.perform(post("/api/restaurants/" + restaurantAId + "/staff")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"correo-invalido"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mvc.perform(post("/api/restaurants/" + restaurantAId + "/staff")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"  "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mvc.perform(post("/api/restaurants/" + restaurantAId + "/staff")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(ownerA.email())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("propietario")));

        mvc.perform(get("/api/restaurants/0/staff")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerA.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mvc.perform(delete("/api/restaurants/" + restaurantAId + "/staff/0")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerA.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // 404 missing user email or missing restaurant
        mvc.perform(post("/api/restaurants/" + restaurantAId + "/staff")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"no-existe@example.test"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        mvc.perform(get("/api/restaurants/999999/staff")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerA.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        // 201 assign staffA -> persists row in restaurant_staff
        mvc.perform(post("/api/restaurants/" + restaurantAId + "/staff")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(staffA.email())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(staffA.userId()))
                .andExpect(jsonPath("$.email").value(staffA.email()))
                .andExpect(jsonPath("$.assignedAt").exists());

        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM restaurant_staff WHERE restaurant_id = ? AND user_id = ?",
                Integer.class, restaurantAId, staffA.userId()));

        // 409 duplicate staff assignment -> single row preserved
        mvc.perform(post("/api/restaurants/" + restaurantAId + "/staff")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(staffA.email())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(containsString("ya es empleado")));

        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM restaurant_staff WHERE restaurant_id = ?",
                Integer.class, restaurantAId));

        // 200 GET staff list
        mvc.perform(get("/api/restaurants/" + restaurantAId + "/staff")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].userId").value(staffA.userId()))
                .andExpect(jsonPath("$[0].email").value(staffA.email()));

        // DAT-02 DB constraints: duplicate SQL insert rejected by uq_staff_user_restaurant,
        // and ON DELETE RESTRICT blocks deleting referenced user or restaurant
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO restaurant_staff (user_id, restaurant_id) VALUES (?, ?)",
                staffA.userId(), restaurantAId));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "DELETE FROM users WHERE user_id = ?",
                staffA.userId()));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "DELETE FROM restaurants WHERE restaurant_id = ?",
                restaurantAId));

        // 204 DELETE staff removes link while preserving user account
        mvc.perform(delete("/api/restaurants/" + restaurantAId + "/staff/" + staffA.userId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerA.token()))
                .andExpect(status().isNoContent());

        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM restaurant_staff WHERE restaurant_id = ? AND user_id = ?",
                Integer.class, restaurantAId, staffA.userId()));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE user_id = ?",
                Integer.class, staffA.userId()));

        // Repeating DELETE or listing after unlinking returns 404 and []
        mvc.perform(delete("/api/restaurants/" + restaurantAId + "/staff/" + staffA.userId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerA.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        mvc.perform(get("/api/restaurants/" + restaurantAId + "/staff")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void crossRestaurantIsolationBlocksForeignOwnersStaffAndOutsidersWithZeroSqlSideEffects() throws Exception {
        Actor ownerA = registerAndLogin("iso-owner-a");
        Actor ownerB = registerAndLogin("iso-owner-b");
        Actor staffA = registerAndLogin("iso-staff-a");
        Actor outsider = registerAndLogin("iso-outsider");

        int restaurantAId = createRestaurant(ownerA.token(), """
                {"name":"Restaurante A Original","description":"Desc A","fullAddress":"Dir A #1-1"}
                """, null);
        int restaurantBId = createRestaurant(ownerB.token(), """
                {"name":"Restaurante B Original","description":"Desc B","fullAddress":"Dir B #2-2"}
                """, null);

        // Link staffA strictly to restaurantA
        mvc.perform(post("/api/restaurants/" + restaurantAId + "/staff")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(staffA.email())))
                .andExpect(status().isCreated());

        // ownerB, staffA (of restaurantA), and outsider MUST all receive 403 on restaurantA management endpoints
        for (Actor unauthorizedCaller : List.of(ownerB, staffA, outsider)) {
            assertAllManagementRoutesForbidden(unauthorizedCaller.token(), restaurantAId, staffA.userId(), outsider.email());
        }

        // ownerA and staffA MUST receive 403 on restaurantB management endpoints
        for (Actor unauthorizedCaller : List.of(ownerA, staffA)) {
            assertAllManagementRoutesForbidden(unauthorizedCaller.token(), restaurantBId, ownerB.userId(), outsider.email());
        }

        // SQL non-modification verification on restaurants & restaurant_staff
        Map<String, Object> rowA = jdbc.queryForMap(
                "SELECT restaurant_name, restaurant_owner_id, restaurant_description, restaurant_full_address "
                        + "FROM restaurants WHERE restaurant_id = ?",
                restaurantAId);
        assertEquals("Restaurante A Original", rowA.get("restaurant_name"));
        assertEquals(ownerA.userId(), rowA.get("restaurant_owner_id"));
        assertEquals("Desc A", rowA.get("restaurant_description"));
        assertEquals("Dir A #1-1", rowA.get("restaurant_full_address"));

        Map<String, Object> rowB = jdbc.queryForMap(
                "SELECT restaurant_name, restaurant_owner_id, restaurant_description, restaurant_full_address "
                        + "FROM restaurants WHERE restaurant_id = ?",
                restaurantBId);
        assertEquals("Restaurante B Original", rowB.get("restaurant_name"));
        assertEquals(ownerB.userId(), rowB.get("restaurant_owner_id"));
        assertEquals("Desc B", rowB.get("restaurant_description"));
        assertEquals("Dir B #2-2", rowB.get("restaurant_full_address"));

        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM restaurant_staff WHERE restaurant_id = ?",
                Integer.class, restaurantAId));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM restaurant_staff WHERE restaurant_id = ?",
                Integer.class, restaurantBId));
    }

    @Test
    void promotionPublishingHonorsRest05ForOwnerAndLinkedStaffAndRevokesImmediatelyAfterUnlink() throws Exception {
        Actor ownerA = registerAndLogin("promo-owner-a");
        Actor ownerB = registerAndLogin("promo-owner-b");
        Actor staffA = registerAndLogin("promo-staff-a");
        Actor outsider = registerAndLogin("promo-outsider");

        int restaurantAId = createRestaurant(ownerA.token(), """
                {"name":"Promo Resto A","fullAddress":"Calle A #10"}
                """, null);
        int restaurantBId = createRestaurant(ownerB.token(), """
                {"name":"Promo Resto B","fullAddress":"Calle B #20"}
                """, null);

        // 401 & 404 & 400 checks on POST /api/promociones
        mvc.perform(post("/api/promociones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(promoPayload(restaurantAId, "Sin Token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mvc.perform(post("/api/promociones")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer qa-fallback-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(promoPayload(restaurantAId, "Sin X-User-Id")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mvc.perform(post("/api/promociones")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "restaurantId": %d,
                                  "name": "Sin Limite ni Fecha",
                                  "applicableProducts": "Menu",
                                  "type": "PERCENTAGE",
                                  "amount": 20
                                }
                                """.formatted(restaurantAId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mvc.perform(post("/api/promociones")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(promoPayload(999999, "Restaurante Inexistente")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        // 1. Owner A publishes on Restaurant A -> 201 Created
        MvcResult ownerPromoResult = mvc.perform(post("/api/promociones")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(promoPayload(restaurantAId, "Promo Owner A")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.restaurantId").value(restaurantAId))
                .andExpect(jsonPath("$.status").value("Active"))
                .andReturn();
        int ownerDiscountId = JsonPath.read(ownerPromoResult.getResponse().getContentAsString(), "$.discountId");
        assertEquals("Active", jdbc.queryForObject(
                "SELECT discount_status::text FROM discounts WHERE discount_id = ?",
                String.class, ownerDiscountId));

        // 2. Unlinked staffA, outsider, and ownerB cannot publish on Restaurant A -> 403
        for (Actor caller : List.of(staffA, outsider, ownerB)) {
            mvc.perform(post("/api/promociones")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + caller.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(promoPayload(restaurantAId, "Bloqueado " + caller.userId())))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
        }

        // 3. Link staffA to Restaurant A -> 201
        mvc.perform(post("/api/restaurants/" + restaurantAId + "/staff")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(staffA.email())))
                .andExpect(status().isCreated());

        // 4. REST-05: Linked staffA publishes on Restaurant A -> 201 Created, persisted in PostgreSQL
        MvcResult staffPromoResult = mvc.perform(post("/api/promociones")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + staffA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(promoPayload(restaurantAId, "Promo Staff REST-05")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.restaurantId").value(restaurantAId))
                .andExpect(jsonPath("$.name").value("Promo Staff REST-05"))
                .andExpect(jsonPath("$.status").value("Active"))
                .andReturn();
        int staffDiscountId = JsonPath.read(staffPromoResult.getResponse().getContentAsString(), "$.discountId");
        assertEquals(restaurantAId, jdbc.queryForObject(
                "SELECT discount_restaurant_id FROM discounts WHERE discount_id = ?",
                Integer.class, staffDiscountId));
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM discounts WHERE discount_restaurant_id = ?",
                Integer.class, restaurantAId));

        // 5. Cross-restaurant isolation for linked staffA: publishing on Restaurant B -> 403 Forbidden
        mvc.perform(post("/api/promociones")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + staffA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(promoPayload(restaurantBId, "Staff A en Resto B")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM discounts WHERE discount_restaurant_id = ?",
                Integer.class, restaurantBId));

        // 6. Owner A unlinks staffA -> 204 No Content, then staffA immediately loses REST-05 publish permission
        mvc.perform(delete("/api/restaurants/" + restaurantAId + "/staff/" + staffA.userId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerA.token()))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/promociones")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + staffA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(promoPayload(restaurantAId, "Staff Desvinculado")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM discounts WHERE discount_restaurant_id = ?",
                Integer.class, restaurantAId));
    }

    // ---------------------------------------------------------------- helpers

    private void assertAllManagementRoutesForbidden(
            String callerToken,
            int targetRestaurantId,
            int targetStaffUserId,
            String candidateEmail) throws Exception {
        mvc.perform(get("/api/restaurantes/" + targetRestaurantId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + callerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        mvc.perform(patch("/api/restaurantes/" + targetRestaurantId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + callerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Intento No Autorizado"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        mvc.perform(post("/api/restaurants/" + targetRestaurantId + "/staff")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + callerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(candidateEmail)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        mvc.perform(get("/api/restaurants/" + targetRestaurantId + "/staff")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + callerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        mvc.perform(delete("/api/restaurants/" + targetRestaurantId + "/staff/" + targetStaffUserId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + callerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    private Actor registerAndLogin(String label) throws Exception {
        String email = label + "-" + UUID.randomUUID() + "@example.test";
        String password = "SecureP@ss123!";

        MvcResult regResult = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "QA",
                                  "lastName": "Tester",
                                  "birthday": "1996-05-10",
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isCreated())
                .andReturn();
        int userId = JsonPath.read(regResult.getResponse().getContentAsString(), "$.id");

        MvcResult loginResult = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        String token = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.token");
        return new Actor(userId, email, token);
    }

    private int createRestaurant(String token, String jsonBody, Integer spoofedHeaderUserId) throws Exception {
        var req = post("/api/restaurantes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody);
        if (spoofedHeaderUserId != null) {
            req.header("X-User-Id", String.valueOf(spoofedHeaderUserId));
        }
        MvcResult result = mvc.perform(req)
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.restaurantId");
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

    private static String promoPayload(int restaurantId, String name) {
        return """
                {
                  "restaurantId": %d,
                  "name": "%s",
                  "applicableProducts": "Platos seleccionados",
                  "type": "PERCENTAGE",
                  "amount": 20.00,
                  "maxUses": 50
                }
                """.formatted(restaurantId, name);
    }

    private record Actor(int userId, String email, String token) {
    }
}
