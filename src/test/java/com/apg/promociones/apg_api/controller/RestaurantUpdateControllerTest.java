package com.apg.promociones.apg_api.controller;

import com.apg.promociones.apg_api.exception.GlobalExceptionHandler;
import com.apg.promociones.apg_api.model.Restaurant;
import com.apg.promociones.apg_api.model.User;
import com.apg.promociones.apg_api.repository.RestaurantRepository;
import com.apg.promociones.apg_api.repository.UserRepository;
import com.apg.promociones.apg_api.security.SimulatedAuthFilter;
import com.apg.promociones.apg_api.security.SimulatedAuthProperties;
import com.apg.promociones.apg_api.service.LoginServices.JwtService;
import com.apg.promociones.apg_api.service.RestaurantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RestaurantUpdateControllerTest {

    private static final String JWT_SECRET = "MiClaveSuperSecretaYLoSuficientementeLargaParaHS25612345";

    @Mock
    private UserRepository userRepository;

    @Mock
    private RestaurantRepository restaurantRepository;

    private MockMvc mockMvc;
    private User owner;
    private User otherUser;
    private Restaurant restaurant;
    private String ownerToken;
    private String otherUserToken;

    @BeforeEach
    void setUp() {
        JwtService jwtService = new JwtService(JWT_SECRET, 86_400_000L);
        RestaurantService restaurantService = new RestaurantService(userRepository, restaurantRepository);

        SimulatedAuthProperties authProperties = new SimulatedAuthProperties();
        authProperties.setEnabled(true);
        authProperties.setToken("fallback-restaurant-update-token");

        mockMvc = MockMvcBuilders.standaloneSetup(new RestaurantController(restaurantService))
                .addFilters(new SimulatedAuthFilter(authProperties, jwtService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        owner = User.builder()
                .id(5)
                .firstName("Ana")
                .lastName("Perez")
                .email("ana@example.com")
                .build();

        otherUser = User.builder()
                .id(99)
                .firstName("Carlos")
                .lastName("Gomez")
                .email("carlos@example.com")
                .build();

        restaurant = Restaurant.builder()
                .id(10)
                .name("La Cazuela")
                .owner(owner)
                .description("Comida típica")
                .fullAddress("Calle 45 #12-30")
                .creationDate(LocalDateTime.of(2026, 11, 13, 10, 0))
                .build();

        ownerToken = jwtService.generateToken(owner);
        otherUserToken = jwtService.generateToken(otherUser);
    }

    @Test
    void returns200AndUpdatesAllEditableFieldsWhenCalledByJwtOwner() throws Exception {
        when(restaurantRepository.findById(10)).thenReturn(Optional.of(restaurant));
        when(restaurantRepository.saveAndFlush(any(Restaurant.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(patch("/api/restaurantes/10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "  La Cazuela Criolla Gourmet  ",
                                  "description": "  Cocina típica renovada  ",
                                  "fullAddress": "  Carrera 27 #34-10, Bucaramanga  "
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantId").value(10))
                .andExpect(jsonPath("$.name").value("La Cazuela Criolla Gourmet"))
                .andExpect(jsonPath("$.ownerId").value(5))
                .andExpect(jsonPath("$.description").value("Cocina típica renovada"))
                .andExpect(jsonPath("$.fullAddress").value("Carrera 27 #34-10, Bucaramanga"))
                .andExpect(jsonPath("$.message").value("Restaurante actualizado exitosamente."));

        verify(restaurantRepository).saveAndFlush(restaurant);
        assertEquals(5, restaurant.getOwner().getId());
    }

    @Test
    void returns200OnPartialUpdateAndClearsDescriptionWhenNull() throws Exception {
        when(restaurantRepository.findById(10)).thenReturn(Optional.of(restaurant));
        when(restaurantRepository.saveAndFlush(any(Restaurant.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(patch("/api/restaurantes/10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Nuevo Nombre"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nuevo Nombre"))
                .andExpect(jsonPath("$.description").value("Comida típica"))
                .andExpect(jsonPath("$.fullAddress").value("Calle 45 #12-30"))
                .andExpect(jsonPath("$.ownerId").value(5));

        mockMvc.perform(patch("/api/restaurantes/10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":null}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nuevo Nombre"))
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.fullAddress").value("Calle 45 #12-30"));
    }

    @Test
    void returns400WhenAttemptingOwnershipReassignmentOrInvalidPayload() throws Exception {
        mockMvc.perform(patch("/api/restaurantes/10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Hack","ownerId":99}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("ownerId")));

        mockMvc.perform(patch("/api/restaurantes/10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"restaurantOwnerId":99}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("restaurantOwnerId")));

        mockMvc.perform(patch("/api/restaurantes/10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(patch("/api/restaurantes/10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(patch("/api/restaurantes/10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullAddress":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(patch("/api/restaurantes/10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(patch("/api/restaurantes/0")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Valido"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(patch("/api/restaurantes/abc")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Valido"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(restaurantRepository);
    }

    @Test
    void returns401WhenBearerTokenIsMissingTamperedOrUnidentified() throws Exception {
        mockMvc.perform(patch("/api/restaurantes/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Nuevo"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        String tampered = ownerToken.substring(0, ownerToken.length() - 4) + "XXXX";
        mockMvc.perform(patch("/api/restaurantes/10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Nuevo"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(patch("/api/restaurantes/10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer fallback-restaurant-update-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Nuevo"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Could not identify the authenticated user."));

        verifyNoInteractions(restaurantRepository);
    }

    @Test
    void returns403WhenAuthenticatedUserIsNotRestaurantOwner() throws Exception {
        when(restaurantRepository.findById(10)).thenReturn(Optional.of(restaurant));

        mockMvc.perform(patch("/api/restaurantes/10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Restaurante Secuestrado"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        verify(restaurantRepository, never()).saveAndFlush(any());
        assertEquals("La Cazuela", restaurant.getName());
        assertEquals(5, restaurant.getOwner().getId());
    }

    @Test
    void returns404WhenRestaurantDoesNotExist() throws Exception {
        when(restaurantRepository.findById(999)).thenReturn(Optional.empty());

        mockMvc.perform(patch("/api/restaurantes/999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Inexistente"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Restaurant not found."));

        verify(restaurantRepository, never()).saveAndFlush(any());
    }
}
