package com.apg.promociones.apg_api.controller;

import com.apg.promociones.apg_api.dto.CreateRestaurantRequest;
import com.apg.promociones.apg_api.dto.CreateRestaurantResponse;
import com.apg.promociones.apg_api.exception.GlobalExceptionHandler;
import com.apg.promociones.apg_api.security.SimulatedAuthFilter;
import com.apg.promociones.apg_api.security.SimulatedAuthProperties;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RestaurantControllerTest {

    @Mock
    private RestaurantService restaurantService;

    private final SimulatedAuthProperties authProperties = new SimulatedAuthProperties();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authProperties.setToken("restaurant-test-token");
        mockMvc = MockMvcBuilders.standaloneSetup(new RestaurantController(restaurantService))
                .addFilters(new SimulatedAuthFilter(authProperties))
                .defaultRequest(post("/")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer restaurant-test-token")
                        .header("X-User-Id", "5"))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returns201WhenRestaurantCreatedSuccessfully() throws Exception {
        CreateRestaurantResponse response = CreateRestaurantResponse.builder()
                .restaurantId(10)
                .name("La Cazuela")
                .ownerId(5)
                .description("Comida típica")
                .fullAddress("Calle 45 #12-30")
                .creationDate(LocalDateTime.of(2026, 11, 13, 10, 0))
                .message("Restaurante registrado exitosamente.")
                .build();

        when(restaurantService.create(eq(5), any(CreateRestaurantRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/restaurantes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"La Cazuela\",\"description\":\"Comida típica\",\"fullAddress\":\"Calle 45 #12-30\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.restaurantId").value(10))
                .andExpect(jsonPath("$.name").value("La Cazuela"))
                .andExpect(jsonPath("$.ownerId").value(5))
                .andExpect(jsonPath("$.description").value("Comida típica"))
                .andExpect(jsonPath("$.fullAddress").value("Calle 45 #12-30"))
                .andExpect(jsonPath("$.message").value("Restaurante registrado exitosamente."));
    }

    @Test
    void returns201WithNullDescription() throws Exception {
        CreateRestaurantResponse response = CreateRestaurantResponse.builder()
                .restaurantId(11)
                .name("Sushi Express")
                .ownerId(5)
                .fullAddress("Carrera 33 #48-15")
                .creationDate(LocalDateTime.of(2026, 11, 13, 10, 0))
                .message("Restaurante registrado exitosamente.")
                .build();

        when(restaurantService.create(eq(5), any(CreateRestaurantRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/restaurantes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sushi Express\",\"fullAddress\":\"Carrera 33 #48-15\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.restaurantId").value(11))
                .andExpect(jsonPath("$.description").doesNotExist());
    }

    @Test
    void returns400WhenNameIsMissing() throws Exception {
        mockMvc.perform(post("/api/restaurantes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullAddress\":\"Calle 45 #12-30\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(restaurantService);
    }

    @Test
    void returns400WhenNameIsBlank() throws Exception {
        mockMvc.perform(post("/api/restaurantes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \",\"fullAddress\":\"Calle 45 #12-30\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(restaurantService);
    }

    @Test
    void returns400WhenAddressIsMissing() throws Exception {
        mockMvc.perform(post("/api/restaurantes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"La Cazuela\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(restaurantService);
    }

    @Test
    void returns400WhenBodyIsEmpty() throws Exception {
        mockMvc.perform(post("/api/restaurantes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(restaurantService);
    }

    @Test
    void returns401WithoutBearerToken() throws Exception {
        MockMvc unauthenticatedMvc = MockMvcBuilders.standaloneSetup(new RestaurantController(restaurantService))
                .addFilters(new SimulatedAuthFilter(authProperties))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        unauthenticatedMvc.perform(post("/api/restaurantes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"La Cazuela\",\"fullAddress\":\"Calle 45 #12-30\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(restaurantService);
    }
}
