package com.apg.promociones.apg_api.security;

import com.apg.promociones.apg_api.controller.AuthController;
import com.apg.promociones.apg_api.controller.CanjeController;
import com.apg.promociones.apg_api.controller.CouponController;
import com.apg.promociones.apg_api.controller.PromocionController;
import com.apg.promociones.apg_api.dto.ActivePromotionResponse;
import com.apg.promociones.apg_api.dto.AssociatePromotionRequest;
import com.apg.promociones.apg_api.dto.AssociatePromotionResponse;
import com.apg.promociones.apg_api.dto.CanjeRequest;
import com.apg.promociones.apg_api.dto.CanjeResponse;
import com.apg.promociones.apg_api.dto.PromocionRequest;
import com.apg.promociones.apg_api.dto.PromocionResponse;
import com.apg.promociones.apg_api.dto.UserCouponResponse;
import com.apg.promociones.apg_api.exception.GlobalExceptionHandler;
import com.apg.promociones.apg_api.model.Coupon;
import com.apg.promociones.apg_api.model.Discount;
import com.apg.promociones.apg_api.model.Restaurant;
import com.apg.promociones.apg_api.model.User;
import com.apg.promociones.apg_api.model.enums.CouponStatus;
import com.apg.promociones.apg_api.model.enums.DiscountStatus;
import com.apg.promociones.apg_api.model.enums.DiscountType;
import com.apg.promociones.apg_api.repository.CouponRepository;
import com.apg.promociones.apg_api.repository.DiscountRepository;
import com.apg.promociones.apg_api.repository.RestaurantRepository;
import com.apg.promociones.apg_api.repository.UserRepository;
import com.apg.promociones.apg_api.service.AssociatePromotionService;
import com.apg.promociones.apg_api.service.AuthService;
import com.apg.promociones.apg_api.service.CanjeService;
import com.apg.promociones.apg_api.service.LoginServices.JwtService;
import com.apg.promociones.apg_api.service.PromocionService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("Security & Authentication Full Integration Tests")
class SecurityAndAuthIntegrationTest {

    private static final String JWT_SECRET = "MiClaveSuperSecretaYLoSuficientementeLargaParaHS25612345";
    private static final long JWT_EXPIRATION_MS = 86_400_000L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DiscountRepository discountRepository;

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private AssociatePromotionService associatePromotionService;

    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private AuthService authService;
    private PromocionService promocionService;
    private CanjeService canjeService;
    private SimulatedAuthFilter authFilter;
    private MockMvc mockMvc;

    private User testUser10;
    private User testUser99;
    private Restaurant restaurantUser10;
    private Restaurant restaurantUser99;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        jwtService = new JwtService(JWT_SECRET, JWT_EXPIRATION_MS);
        authService = new AuthService(userRepository, passwordEncoder, jwtService);
        promocionService = new PromocionService(discountRepository, restaurantRepository);
        canjeService = new CanjeService(couponRepository, discountRepository);

        SimulatedAuthProperties properties = new SimulatedAuthProperties();
        properties.setEnabled(true);
        properties.setToken("fallback-test-token");

        authFilter = new SimulatedAuthFilter(properties, jwtService);

        AuthController authController = new AuthController(authService);
        CouponController couponController = new CouponController(associatePromotionService);
        PromocionController promocionController = new PromocionController(promocionService);
        CanjeController canjeController = new CanjeController(canjeService);

        mockMvc = MockMvcBuilders.standaloneSetup(authController, couponController, promocionController, canjeController)
                .addFilters(authFilter)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        testUser10 = User.builder()
                .id(10)
                .firstName("Ana")
                .secondName("Maria")
                .lastName("Lopez")
                .birthday(LocalDate.of(2000, 1, 2))
                .email("ana.lopez@example.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .joinDate(LocalDateTime.now())
                .build();

        testUser99 = User.builder()
                .id(99)
                .firstName("Carlos")
                .lastName("Gomez")
                .birthday(LocalDate.of(1995, 5, 10))
                .email("carlos.gomez@example.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .joinDate(LocalDateTime.now())
                .build();

        restaurantUser10 = Restaurant.builder()
                .id(1)
                .name("Restaurante de Ana")
                .owner(testUser10)
                .fullAddress("Calle 123")
                .build();

        restaurantUser99 = Restaurant.builder()
                .id(2)
                .name("Restaurante de Carlos")
                .owner(testUser99)
                .fullAddress("Avenida 456")
                .build();
    }

    // =========================================================================
    // 1. REGISTRO DE USUARIOS
    // =========================================================================
    @Nested
    @DisplayName("User Registration (/api/auth/register)")
    class RegistrationTests {

        @Test
        @DisplayName("Valid registration returns 201 Created and never exposes password or hash")
        void validRegistration_Returns201AndHidesPassword() throws Exception {
            String email = "nuevo.usuario@example.com";
            when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
                User u = invocation.getArgument(0);
                u.setId(101);
                return u;
            });

            String body = """
                    {
                        "firstName": "Pedro",
                        "secondName": "Jose",
                        "lastName": "Ramirez",
                        "birthday": "1998-07-15",
                        "email": "%s",
                        "password": "SecurePassword123!"
                    }
                    """.formatted(email);

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(101))
                    .andExpect(jsonPath("$.firstName").value("Pedro"))
                    .andExpect(jsonPath("$.email").value(email))
                    .andExpect(jsonPath("$.joinDate").exists())
                    .andExpect(jsonPath("$.password").doesNotExist())
                    .andExpect(jsonPath("$.passwordHash").doesNotExist());
        }

        @Test
        @DisplayName("Validation constraint violations return 400 Bad Request")
        void invalidInputs_Returns400BadRequest() throws Exception {
            // Invalid email
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "firstName": "Pedro",
                                        "lastName": "Ramirez",
                                        "birthday": "1998-07-15",
                                        "email": "not-a-valid-email",
                                        "password": "Password123!"
                                    }
                                    """))
                    .andExpect(status().isBadRequest());

            // Password exceeding 72-byte BCrypt limit
            String longPassword = "A".repeat(73);
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "firstName": "Pedro",
                                        "lastName": "Ramirez",
                                        "birthday": "1998-07-15",
                                        "email": "long@example.com",
                                        "password": "%s"
                                    }
                                    """.formatted(longPassword)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("72-byte BCrypt limit")));

            // Empty body
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Duplicate email registration returns 409 Conflict")
        void duplicateEmail_Returns409Conflict() throws Exception {
            String duplicateEmail = "ana.lopez@example.com";
            when(userRepository.saveAndFlush(any(User.class)))
                    .thenThrow(new DataIntegrityViolationException("unique_email constraint"));
            when(userRepository.existsByEmail(duplicateEmail)).thenReturn(true);

            String body = """
                    {
                        "firstName": "Ana",
                        "lastName": "Lopez",
                        "birthday": "2000-01-02",
                        "email": "%s",
                        "password": "Password123!"
                    }
                    """.formatted(duplicateEmail);

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.message").value("Email address is already registered."));
        }
    }

    // =========================================================================
    // 2. AUTENTICACIÓN JWT Y LOGIN
    // =========================================================================
    @Nested
    @DisplayName("JWT Authentication & Login (/api/auth/login)")
    class LoginTests {

        @Test
        @DisplayName("Valid login returns 200 with genuine signed JWT and no password in response")
        void validLogin_Returns200WithRealJwtToken() throws Exception {
            when(userRepository.findByEmail("ana.lopez@example.com")).thenReturn(Optional.of(testUser10));

            MvcResult result = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"ana.lopez@example.com","password":"Password123!"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isString())
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.userId").value(10))
                    .andExpect(jsonPath("$.email").value("ana.lopez@example.com"))
                    .andExpect(jsonPath("$.message").value("Authentication successful."))
                    .andExpect(jsonPath("$.password").doesNotExist())
                    .andExpect(jsonPath("$.passwordHash").doesNotExist())
                    .andReturn();

            String token = result.getResponse().getContentAsString()
                    .split("\"token\":\"")[1].split("\"")[0];

            assertTrue(jwtService.isTokenValid(token));
            org.junit.jupiter.api.Assertions.assertEquals(10, jwtService.extractUserId(token));
        }

        @Test
        @DisplayName("Invalid credentials return 401 Unauthorized with generic message (RN-17)")
        void invalidCredentials_Returns401() throws Exception {
            // User not found
            when(userRepository.findByEmail("inexistente@example.com")).thenReturn(Optional.empty());
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"inexistente@example.com","password":"Password123!"}
                                    """))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.message").value("Invalid credentials."));

            // Wrong password
            when(userRepository.findByEmail("ana.lopez@example.com")).thenReturn(Optional.of(testUser10));
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"ana.lopez@example.com","password":"WrongPassword!"}
                                    """))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.message").value("Invalid credentials."));
        }
    }

    // =========================================================================
    // 3. CONTROL DE ACCESO REST: 401 (SIN TOKEN / TOKEN INVÁLIDO / EXPIRADO)
    // =========================================================================
    @Nested
    @DisplayName("Protected Endpoints - Token Validation (401)")
    class TokenValidationTests {

        @Test
        @DisplayName("Access without token returns 401 Unauthorized")
        void noToken_Returns401() throws Exception {
            mockMvc.perform(get("/api/promociones/activas"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.message").value(containsString("Bearer token is missing")));
        }

        @Test
        @DisplayName("Access with tampered or invalid token returns 401 Unauthorized")
        void invalidOrTamperedToken_Returns401() throws Exception {
            String validJwt = jwtService.generateToken(testUser10);
            String tamperedJwt = validJwt.substring(0, validJwt.length() - 5) + "ABCDE";

            mockMvc.perform(get("/api/promociones/activas")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tamperedJwt))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.message").value("The Bearer token is invalid or expired."));
        }

        @Test
        @DisplayName("Access with expired JWT token returns 401 Unauthorized")
        void expiredToken_Returns401() throws Exception {
            SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
            Date now = new Date();
            Date expiredDate = new Date(now.getTime() - 100_000); // Expired in the past

            String expiredJwt = Jwts.builder()
                    .subject("10")
                    .claim("email", "ana.lopez@example.com")
                    .issuedAt(new Date(now.getTime() - 200_000))
                    .expiration(expiredDate)
                    .signWith(key)
                    .compact();

            mockMvc.perform(get("/api/promociones/activas")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredJwt))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.message").value("The Bearer token is invalid or expired."));
        }

        @Test
        @DisplayName("Access with valid signed JWT returns 200 OK")
        void validJwt_Returns200() throws Exception {
            String jwt = jwtService.generateToken(testUser10);
            when(discountRepository.findActivePromotions()).thenReturn(List.of());

            mockMvc.perform(get("/api/promociones/activas")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt))
                    .andExpect(status().isOk());
        }
    }

    // =========================================================================
    // 4. AUTORIZACIÓN CONTEXTUAL Y CONTROL DE PROPIEDAD: 403 FORBIDDEN
    // =========================================================================
    @Nested
    @DisplayName("Contextual Authorization & Resource Ownership (403 vs 200/201)")
    class ContextualAuthorizationTests {

        @Test
        @DisplayName("User accessing their own coupons returns 200 OK")
        void ownCoupons_Returns200() throws Exception {
            String jwtUser10 = jwtService.generateToken(testUser10);
            when(associatePromotionService.getUserCoupons(10)).thenReturn(List.of());

            mockMvc.perform(get("/api/coupons/usuario/10")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtUser10))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("User accessing another user's coupons returns 403 Forbidden")
        void otherUserCoupons_Returns403() throws Exception {
            String jwtUser10 = jwtService.generateToken(testUser10); // Authenticated as User 10

            // Attempts to read User 99's coupons
            mockMvc.perform(get("/api/coupons/usuario/99")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtUser10))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.message").value(containsString("no coincide con el ID solicitado")));
        }

        @Test
        @DisplayName("User associating promotion with another user's ID returns 403 Forbidden")
        void associateForOtherUser_Returns403() throws Exception {
            String jwtUser10 = jwtService.generateToken(testUser10); // User 10

            // Request body asks to associate promotion for User 99
            String body = """
                    {
                        "userId": 99,
                        "promotionId": 5
                    }
                    """;

            mockMvc.perform(post("/api/coupons")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtUser10)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.message").value(containsString("no puede asociar promociones a otro usuario")));
        }

        @Test
        @DisplayName("User associating promotion for themselves returns 201 Created")
        void associateForSelf_Returns201() throws Exception {
            String jwtUser10 = jwtService.generateToken(testUser10);

            AssociatePromotionRequest request = new AssociatePromotionRequest(10, 5);
            AssociatePromotionResponse response = AssociatePromotionResponse.builder()
                    .couponId(501)
                    .userId(10)
                    .promotionId(5)
                    .couponHash("test-hash-uuid")
                    .status("UNUSED")
                    .claimTimestamp(LocalDateTime.now())
                    .message("Promoción guardada en el perfil del cliente.")
                    .build();

            when(associatePromotionService.associate(10, request)).thenReturn(response);

            mockMvc.perform(post("/api/coupons")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtUser10)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"userId":10,"promotionId":5}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.couponId").value(501))
                    .andExpect(jsonPath("$.userId").value(10));
        }

        @Test
        @DisplayName("Publishing promotion for owned restaurant returns 201 Created")
        void publishPromotion_OwnedRestaurant_Returns201() throws Exception {
            String jwtUser10 = jwtService.generateToken(testUser10);
            when(restaurantRepository.findById(1)).thenReturn(Optional.of(restaurantUser10));

            String body = """
                    {
                        "restaurantId": 1,
                        "name": "Combo 2x1",
                        "applicableProducts": "Bebidas",
                        "type": "PERCENTAGE",
                        "amount": 20,
                        "startTimestamp": "%s",
                        "endTimestamp": "%s",
                        "maxUses": 100
                    }
                    """.formatted(
                    LocalDateTime.now().minusDays(1),
                    LocalDateTime.now().plusDays(10));

            mockMvc.perform(post("/api/promociones")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtUser10)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.restaurantId").value(1))
                    .andExpect(jsonPath("$.name").value("Combo 2x1"));
        }

        @Test
        @DisplayName("Publishing promotion for unowned restaurant returns 403 Forbidden")
        void publishPromotion_UnownedRestaurant_Returns403() throws Exception {
            String jwtUser10 = jwtService.generateToken(testUser10); // User 10
            when(restaurantRepository.findById(2)).thenReturn(Optional.of(restaurantUser99)); // Restaurant owned by User 99

            String body = """
                    {
                        "restaurantId": 2,
                        "name": "Intento no autorizado",
                        "applicableProducts": "Platos",
                        "type": "PERCENTAGE",
                        "amount": 15,
                        "maxUses": 50
                    }
                    """;

            mockMvc.perform(post("/api/promociones")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtUser10)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.message").value(containsString("Usuario no tiene permiso para crear promociones")));
        }

        @Test
        @DisplayName("Redeeming coupon for owned restaurant returns 201 Created")
        void redeemCoupon_OwnedRestaurant_Returns201() throws Exception {
            String jwtUser10 = jwtService.generateToken(testUser10);

            Discount discount = Discount.builder()
                    .id(10)
                    .restaurant(restaurantUser10) // Owned by User 10
                    .status(DiscountStatus.Active)
                    .build();

            Coupon coupon = Coupon.builder()
                    .id(200)
                    .couponHash("valid-hash-123")
                    .status(CouponStatus.UNUSED)
                    .discount(discount)
                    .user(testUser99)
                    .build();

            when(couponRepository.findByCouponHashForUpdate("valid-hash-123")).thenReturn(Optional.of(coupon));
            when(discountRepository.findByIdForUpdate(10)).thenReturn(Optional.of(discount));

            mockMvc.perform(post("/api/canjes")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtUser10)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"couponHash":"valid-hash-123"}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("USED"));
        }

        @Test
        @DisplayName("Redeeming coupon for unowned restaurant returns 403 Forbidden")
        void redeemCoupon_UnownedRestaurant_Returns403() throws Exception {
            String jwtUser10 = jwtService.generateToken(testUser10); // User 10

            Discount discount = Discount.builder()
                    .id(10)
                    .restaurant(restaurantUser99) // Owned by User 99!
                    .status(DiscountStatus.Active)
                    .build();

            Coupon coupon = Coupon.builder()
                    .id(200)
                    .couponHash("carlos-hash-456")
                    .status(CouponStatus.UNUSED)
                    .discount(discount)
                    .build();

            when(couponRepository.findByCouponHashForUpdate("carlos-hash-456")).thenReturn(Optional.of(coupon));
            when(discountRepository.findByIdForUpdate(10)).thenReturn(Optional.of(discount));

            mockMvc.perform(post("/api/canjes")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtUser10)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"couponHash":"carlos-hash-456"}
                                    """))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.message").value(containsString("Usuario no tiene permiso para canjear cupones")));
        }
    }

    // =========================================================================
    // 5. CABECERAS SIMULADAS Y PREVENCIÓN DE SUPLANTACIÓN
    // =========================================================================
    @Nested
    @DisplayName("Simulated Headers & Spoofing Rejection")
    class SpoofingPreventionTests {

        @Test
        @DisplayName("Sending X-User-Id without Bearer token is rejected with 401")
        void simulatedHeaderAlone_Returns401() throws Exception {
            mockMvc.perform(get("/api/coupons/usuario/10")
                            .header("X-User-Id", "10"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401));
        }

        @Test
        @DisplayName("Sending X-User-Id alongside valid JWT is ignored; JWT identity strictly enforced")
        void simulatedHeaderWithJwt_UsesJwtIdentityAndRejectsSpoofedAccess() throws Exception {
            String jwtUser10 = jwtService.generateToken(testUser10); // Real token for User 10

            // Attacker sends JWT of User 10, but puts X-User-Id: 99 and tries to read User 99's coupons
            mockMvc.perform(get("/api/coupons/usuario/99")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtUser10)
                            .header("X-User-Id", "99"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.message").value(containsString("no coincide con el ID solicitado")));
        }
    }
}
