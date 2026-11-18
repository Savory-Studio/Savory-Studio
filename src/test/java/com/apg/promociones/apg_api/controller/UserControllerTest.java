package com.apg.promociones.apg_api.controller;

import com.apg.promociones.apg_api.exception.GlobalExceptionHandler;
import com.apg.promociones.apg_api.model.User;
import com.apg.promociones.apg_api.repository.UserRepository;
import com.apg.promociones.apg_api.security.SimulatedAuthFilter;
import com.apg.promociones.apg_api.security.SimulatedAuthProperties;
import com.apg.promociones.apg_api.service.AuthService;
import com.apg.promociones.apg_api.service.LoginServices.JwtService;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private static final String JWT_SECRET = "MiClaveSuperSecretaYLoSuficientementeLargaParaHS25612345";

    @Mock
    private UserRepository userRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private JwtService jwtService;
    private MockMvc mockMvc;
    private User storedUser;
    private String initialHash;
    private String validToken;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(JWT_SECRET, 86_400_000L);
        AuthService authService = new AuthService(userRepository, passwordEncoder, jwtService);

        SimulatedAuthProperties authProperties = new SimulatedAuthProperties();
        authProperties.setEnabled(true);
        authProperties.setToken("fallback-test-token");

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new UserController(authService),
                        new AuthController(authService))
                .addFilters(new SimulatedAuthFilter(authProperties, jwtService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        initialHash = passwordEncoder.encode("Password123!");
        storedUser = User.builder()
                .id(12)
                .firstName("Carlos")
                .lastName("Pérez")
                .email("carlos.perez@example.com")
                .passwordHash(initialHash)
                .build();

        validToken = jwtService.generateToken(storedUser);
    }

    @Test
    void changePasswordReturns200AndUpdatesBcryptHashWhenCurrentPasswordIsValid_CP15() throws Exception {
        when(userRepository.findById(12)).thenReturn(Optional.of(storedUser));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(patch("/api/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"Password123!","newPassword":"NewPassword456!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(12))
                .andExpect(jsonPath("$.email").value("carlos.perez@example.com"))
                .andExpect(jsonPath("$.message").value("Password updated successfully."))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.currentPassword").doesNotExist())
                .andExpect(jsonPath("$.newPassword").doesNotExist());

        verify(userRepository).saveAndFlush(storedUser);
        assertNotEquals(initialHash, storedUser.getPasswordHash());
        assertNotEquals("NewPassword456!", storedUser.getPasswordHash());
        assertTrue(storedUser.getPasswordHash().startsWith("$2a$"));
        assertTrue(passwordEncoder.matches("NewPassword456!", storedUser.getPasswordHash()));
        assertFalse(passwordEncoder.matches("Password123!", storedUser.getPasswordHash()));

        // Verify login rejects old password and accepts the updated password
        when(userRepository.findByEmail("carlos.perez@example.com")).thenReturn(Optional.of(storedUser));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"carlos.perez@example.com","password":"Password123!"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials."));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"carlos.perez@example.com","password":"NewPassword456!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString());
    }

    @Test
    void changePasswordReturns400AndPreservesHashWhenCurrentPasswordIsIncorrect_CP16() throws Exception {
        when(userRepository.findById(12)).thenReturn(Optional.of(storedUser));

        mockMvc.perform(patch("/api/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"WrongPassword!","newPassword":"NewPassword456!"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Current password is incorrect."));

        verify(userRepository, never()).saveAndFlush(any());
        assertEquals(initialHash, storedUser.getPasswordHash());
    }

    @Test
    void changePasswordReturns400WhenNewPasswordIsInvalidOrSameAsCurrent_CP17() throws Exception {
        // Short password (< 8 chars) rejected by Bean Validation
        mockMvc.perform(patch("/api/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"Password123!","newPassword":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // Blank fields rejected by Bean Validation
        mockMvc.perform(patch("/api/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"","newPassword":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // Exceeds 72 UTF-8 bytes
        when(userRepository.findById(12)).thenReturn(Optional.of(storedUser));
        String oversizedPassword = "é".repeat(37); // 74 UTF-8 bytes
        mockMvc.perform(patch("/api/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"Password123!","newPassword":"%s"}
                                """.formatted(oversizedPassword)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Password exceeds the 72-byte BCrypt limit."));

        // New password identical to current password
        mockMvc.perform(patch("/api/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"Password123!","newPassword":"Password123!"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("New password must be different from the current password."));

        verify(userRepository, never()).saveAndFlush(any());
        assertEquals(initialHash, storedUser.getPasswordHash());
    }

    @Test
    void changePasswordReturns401WhenBearerTokenIsMissingOrInvalid_CP18() throws Exception {
        // Missing Authorization header
        mockMvc.perform(patch("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"Password123!","newPassword":"NewPassword456!"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        // Tampered JWT
        String tamperedToken = validToken.substring(0, validToken.length() - 4) + "XXXX";
        mockMvc.perform(patch("/api/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tamperedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"Password123!","newPassword":"NewPassword456!"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        // Fallback token without X-User-Id header
        mockMvc.perform(patch("/api/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer fallback-test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"Password123!","newPassword":"NewPassword456!"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Could not identify the authenticated user."));

        verifyNoInteractions(userRepository);
    }

    @Test
    void changePasswordReturns404WhenAuthenticatedUserDoesNotExist_CP19() throws Exception {
        when(userRepository.findById(12)).thenReturn(Optional.empty());

        mockMvc.perform(patch("/api/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"Password123!","newPassword":"NewPassword456!"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("User not found."));

        verify(userRepository, never()).saveAndFlush(any());
    }
}
