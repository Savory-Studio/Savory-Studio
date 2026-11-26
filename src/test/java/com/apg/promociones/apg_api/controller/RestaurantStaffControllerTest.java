package com.apg.promociones.apg_api.controller;

import com.apg.promociones.apg_api.exception.GlobalExceptionHandler;
import com.apg.promociones.apg_api.model.Restaurant;
import com.apg.promociones.apg_api.model.RestaurantStaff;
import com.apg.promociones.apg_api.model.User;
import com.apg.promociones.apg_api.repository.RestaurantRepository;
import com.apg.promociones.apg_api.repository.RestaurantStaffRepository;
import com.apg.promociones.apg_api.repository.UserRepository;
import com.apg.promociones.apg_api.security.SimulatedAuthFilter;
import com.apg.promociones.apg_api.security.SimulatedAuthProperties;
import com.apg.promociones.apg_api.service.LoginServices.JwtService;
import com.apg.promociones.apg_api.service.RestaurantStaffService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RestaurantStaffControllerTest {

    private static final String JWT_SECRET = "MiClaveSuperSecretaYLoSuficientementeLargaParaHS25612345";

    @Mock
    private RestaurantStaffRepository restaurantStaffRepository;

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private UserRepository userRepository;

    private MockMvc mockMvc;
    private User owner;
    private User otherOwner;
    private User employee;
    private Restaurant ownedRestaurant;
    private String ownerToken;
    private String otherOwnerToken;
    private String employeeToken;

    @BeforeEach
    void setUp() {
        JwtService jwtService = new JwtService(JWT_SECRET, 86_400_000L);
        RestaurantStaffService staffService = new RestaurantStaffService(
                restaurantStaffRepository, restaurantRepository, userRepository);

        SimulatedAuthProperties authProperties = new SimulatedAuthProperties();
        authProperties.setEnabled(true);
        authProperties.setToken("fallback-staff-token");

        mockMvc = MockMvcBuilders.standaloneSetup(new RestaurantStaffController(staffService))
                .addFilters(new SimulatedAuthFilter(authProperties, jwtService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        owner = User.builder()
                .id(5)
                .firstName("Ana")
                .lastName("Perez")
                .email("ana@example.com")
                .build();

        otherOwner = User.builder()
                .id(8)
                .firstName("Mateo")
                .lastName("Ruiz")
                .email("mateo@example.com")
                .build();

        employee = User.builder()
                .id(12)
                .firstName("Laura")
                .lastName("Diaz")
                .email("laura@example.com")
                .build();

        ownedRestaurant = Restaurant.builder()
                .id(10)
                .name("La Cazuela")
                .owner(owner)
                .fullAddress("Calle 45 #12-30")
                .build();

        ownerToken = jwtService.generateToken(owner);
        otherOwnerToken = jwtService.generateToken(otherOwner);
        employeeToken = jwtService.generateToken(employee);
    }

    @Test
    void assignStaffReturns201WhenRequestedByOwner() throws Exception {
        when(restaurantRepository.findById(10)).thenReturn(Optional.of(ownedRestaurant));
        when(userRepository.findByEmail("laura@example.com")).thenReturn(Optional.of(employee));
        when(restaurantStaffRepository.existsByUserIdAndRestaurantId(12, 10)).thenReturn(false);
        when(restaurantStaffRepository.saveAndFlush(any(RestaurantStaff.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/restaurants/10/staff")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"laura@example.com"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(12))
                .andExpect(jsonPath("$.email").value("laura@example.com"))
                .andExpect(jsonPath("$.assignedAt").exists());

        verify(restaurantStaffRepository).saveAndFlush(any(RestaurantStaff.class));
    }

    @Test
    void listStaffReturns200ForOwner() throws Exception {
        RestaurantStaff membership = RestaurantStaff.builder()
                .id(1)
                .restaurant(ownedRestaurant)
                .user(employee)
                .assignedAt(LocalDateTime.of(2026, 11, 20, 14, 0))
                .build();

        when(restaurantRepository.findById(10)).thenReturn(Optional.of(ownedRestaurant));
        when(restaurantStaffRepository.findByRestaurantId(10)).thenReturn(List.of(membership));

        mockMvc.perform(get("/api/restaurants/10/staff")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(12))
                .andExpect(jsonPath("$[0].email").value("laura@example.com"))
                .andExpect(jsonPath("$[0].assignedAt").exists());
    }

    @Test
    void removeStaffReturns204ForOwner() throws Exception {
        RestaurantStaff membership = RestaurantStaff.builder()
                .id(1)
                .restaurant(ownedRestaurant)
                .user(employee)
                .assignedAt(LocalDateTime.of(2026, 11, 20, 14, 0))
                .build();

        when(restaurantRepository.findById(10)).thenReturn(Optional.of(ownedRestaurant));
        when(restaurantStaffRepository.findByUserIdAndRestaurantId(12, 10)).thenReturn(Optional.of(membership));

        mockMvc.perform(delete("/api/restaurants/10/staff/12")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        verify(restaurantStaffRepository).delete(membership);
    }

    @Test
    void returns400ForInvalidPayloadSelfAssignmentOrInvalidIds() throws Exception {
        // Invalid email format
        mockMvc.perform(post("/api/restaurants/10/staff")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // Blank email
        mockMvc.perform(post("/api/restaurants/10/staff")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // Non-positive restaurantId
        mockMvc.perform(get("/api/restaurants/0/staff")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // Non-numeric restaurantId
        mockMvc.perform(get("/api/restaurants/abc/staff")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // Non-positive userId on DELETE
        mockMvc.perform(delete("/api/restaurants/10/staff/0")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(restaurantStaffRepository);

        // Owner self-assignment blocked with 400
        when(restaurantRepository.findById(10)).thenReturn(Optional.of(ownedRestaurant));
        when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(owner));

        mockMvc.perform(post("/api/restaurants/10/staff")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ana@example.com"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("propietario")));

        verify(restaurantStaffRepository, never()).saveAndFlush(any());
    }

    @Test
    void returns401WhenTokenMissingInvalidOrUnidentified() throws Exception {
        mockMvc.perform(get("/api/restaurants/10/staff"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        String tampered = ownerToken.substring(0, ownerToken.length() - 4) + "ZZZZ";
        mockMvc.perform(post("/api/restaurants/10/staff")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"laura@example.com"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(delete("/api/restaurants/10/staff/12")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer fallback-staff-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(restaurantRepository, restaurantStaffRepository, userRepository);
    }

    @Test
    void returns403WithStandardErrorResponseWhenCalledByNonOwnerOrEmployee() throws Exception {
        when(restaurantRepository.findById(10)).thenReturn(Optional.of(ownedRestaurant));

        mockMvc.perform(post("/api/restaurants/10/staff")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"laura@example.com"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value(containsString("Acceso denegado")))
                .andExpect(jsonPath("$.timestamp").exists());

        mockMvc.perform(get("/api/restaurants/10/staff")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value(containsString("Acceso denegado")));

        mockMvc.perform(delete("/api/restaurants/10/staff/12")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherOwnerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value(containsString("Acceso denegado")));

        verifyNoInteractions(restaurantStaffRepository);
    }

    @Test
    void returns404WithStandardErrorResponseWhenRestaurantUserOrAssignmentMissing() throws Exception {
        when(restaurantRepository.findById(999)).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/restaurants/999/staff")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(containsString("999")))
                .andExpect(jsonPath("$.timestamp").exists());

        when(restaurantRepository.findById(10)).thenReturn(Optional.of(ownedRestaurant));
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        mockMvc.perform(post("/api/restaurants/10/staff")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"missing@example.com"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(containsString("missing@example.com")));

        when(restaurantStaffRepository.findByUserIdAndRestaurantId(77, 10)).thenReturn(Optional.empty());
        mockMvc.perform(delete("/api/restaurants/10/staff/77")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(containsString("no es empleado")));
    }

    @Test
    void returns409WithStandardErrorResponseOnDuplicateStaffAssignment() throws Exception {
        when(restaurantRepository.findById(10)).thenReturn(Optional.of(ownedRestaurant));
        when(userRepository.findByEmail("laura@example.com")).thenReturn(Optional.of(employee));
        when(restaurantStaffRepository.existsByUserIdAndRestaurantId(12, 10)).thenReturn(true);

        mockMvc.perform(post("/api/restaurants/10/staff")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"laura@example.com"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(containsString("ya es empleado")))
                .andExpect(jsonPath("$.timestamp").exists());

        when(restaurantStaffRepository.existsByUserIdAndRestaurantId(12, 10)).thenReturn(false);
        when(restaurantStaffRepository.saveAndFlush(any(RestaurantStaff.class)))
                .thenThrow(new DataIntegrityViolationException("uq_staff_user_restaurant"));

        mockMvc.perform(post("/api/restaurants/10/staff")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"laura@example.com"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(containsString("ya es empleado")));
    }
}
