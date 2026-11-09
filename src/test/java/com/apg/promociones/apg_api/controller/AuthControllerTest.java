package com.apg.promociones.apg_api.controller;

import com.apg.promociones.apg_api.dto.ActivePromotionResponse;
import com.apg.promociones.apg_api.exception.GlobalExceptionHandler;
import com.apg.promociones.apg_api.model.User;
import com.apg.promociones.apg_api.repository.UserRepository;
import com.apg.promociones.apg_api.security.SimulatedAuthFilter;
import com.apg.promociones.apg_api.security.SimulatedAuthProperties;
import com.apg.promociones.apg_api.service.AuthService;
import com.apg.promociones.apg_api.service.LoginServices.JwtService;
import com.apg.promociones.apg_api.service.PromocionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private static final String JWT_SECRET = "MiClaveSuperSecretaYLoSuficientementeLargaParaHS25612345";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PromocionService promocionService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private JwtService jwtService;
    private MockMvc mockMvc;
    private User storedUser;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(JWT_SECRET, 86_400_000L);
        AuthService authService = new AuthService(userRepository, passwordEncoder, jwtService);

        SimulatedAuthProperties authProperties = new SimulatedAuthProperties();
        authProperties.setEnabled(true);
        authProperties.setToken("fallback-test-token");

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new AuthController(authService),
                        new PromocionController(promocionService))
                .addFilters(new SimulatedAuthFilter(authProperties, jwtService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        storedUser = User.builder()
                .id(12)
                .firstName("Carlos")
                .lastName("Pérez")
                .email("carlos.perez@example.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .build();
    }

    @Test
    void loginReturns200WithJwtTokenAndUserDetailsWhenCredentialsAreValid_CP08() throws Exception {
        when(userRepository.findByEmail("carlos.perez@example.com")).thenReturn(Optional.of(storedUser));

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"carlos.perez@example.com","password":"Password123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value("86400 seconds"))
                .andExpect(jsonPath("$.userId").value(12))
                .andExpect(jsonPath("$.email").value("carlos.perez@example.com"))
                .andExpect(jsonPath("$.firstName").value("Carlos"))
                .andExpect(jsonPath("$.lastName").value("Pérez"))
                .andExpect(jsonPath("$.message").value("Authentication successful."))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String token = body.split("\"token\":\"")[1].split("\"")[0];
        assertTrue(jwtService.isTokenValid(token));

        when(promocionService.getActivePromotions()).thenReturn(List.of(
                ActivePromotionResponse.builder().discountId(1).name("Promo JWT").status("Active").build()));

        mockMvc.perform(get("/api/promociones/activas")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Promo JWT"));
    }

    @Test
    void loginReturns401WithGenericMessageWhenEmailDoesNotExist_CP09() throws Exception {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"unknown@example.com","password":"Password123!"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid credentials."));
    }

    @Test
    void loginReturns401WithGenericMessageWhenPasswordIsIncorrect_CP10() throws Exception {
        when(userRepository.findByEmail("carlos.perez@example.com")).thenReturn(Optional.of(storedUser));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"carlos.perez@example.com","password":"WrongPassword!"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid credentials."));
    }

    @Test
    void loginReturns400WhenRequiredFieldsAreMissingOrEmailIsInvalid_CP11() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"","password":"Password123!"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"carlos.perez@example.com","password":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":"Password123!"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid email format."));
    }
}
