package com.apg.promociones.apg_api.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.simulated-auth.enabled=true")
@AutoConfigureMockMvc
@Testcontainers
class RegistrationIntegrationTest {

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
    private PasswordEncoder encoder;

    @Test
    void publicRegistrationPersistsTheProfileAndOnlyReturnsPublicFields() throws Exception {
        String email = "new-user@example.test";
        register(request(email, "Password123"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.firstName").value("Ana"))
                .andExpect(jsonPath("$.secondName").value("Maria"))
                .andExpect(jsonPath("$.lastName").value("Lopez"))
                .andExpect(jsonPath("$.birthday").value("2000-01-02"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.joinDate").isString())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        String hash = jdbc.queryForObject("SELECT user_password_hash FROM users WHERE user_email = ?",
                String.class, email);
        assertTrue(encoder.matches("Password123", hash));
    }

    @Test
    void concurrentRegistrationsReturnOneCreatedAndOneConflict() throws Exception {
        String email = "duplicate@example.test";
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> submit = () -> {
            start.await();
            return register(request(email, "Password123")).andReturn().getResponse().getStatus();
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(submit);
            var second = executor.submit(submit);
            start.countDown();
            assertEquals(List.of(201, 409), List.of(first.get(), second.get()).stream().sorted().toList());
        }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE user_email = ?", Integer.class, email));
    }

    @Test
    void rejectsInvalidEmailAndPasswordsOutsideTheSupportedRange() throws Exception {
        register(request("bad-address", "Password123")).andExpect(status().isBadRequest());
        register(request("short@example.test", "short")).andExpect(status().isBadRequest());
        register(request("long@example.test", "é".repeat(37))).andExpect(status().isBadRequest());
    }

    @Test
    void acceptsLegacyBirthDateWithAnOmittedSecondName() throws Exception {
        String payload = """
                {"firstName":"Ana","lastName":"Lopez","birthDate":"2000-01-02",
                 "email":"legacy@example.test","password":"Password123"}
                """;
        register(payload).andExpect(status().isCreated()).andExpect(jsonPath("$.birthday").value("2000-01-02"));
    }

    @Test
    void rejectsMissingAndUnreadableBodies() throws Exception {
        register("{}").andExpect(status().isBadRequest());
        register("{").andExpect(status().isBadRequest());
        register("").andExpect(status().isBadRequest());
    }

    @Test
    void publicRegistrationExceptionDoesNotExposeOtherRoutes() throws Exception {
        mvc.perform(post("/api/coupons").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions register(String body) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String request(String email, String password) {
        return """
                {"firstName":"Ana","secondName":"Maria","lastName":"Lopez","birthday":"2000-01-02",
                 "email":"%s","password":"%s"}
                """.formatted(email, password);
    }
}
