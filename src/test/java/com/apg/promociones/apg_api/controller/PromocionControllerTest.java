package com.apg.promociones.apg_api.controller;

import com.apg.promociones.apg_api.dto.PromocionRequest;
import com.apg.promociones.apg_api.dto.PromocionResponse;
import com.apg.promociones.apg_api.exception.GlobalExceptionHandler;
import com.apg.promociones.apg_api.service.PromocionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PromocionControllerTest {

    private static final String VALID_FIELDS = """
            "restaurantId":1,"name":"Lunch promotion","applicableProducts":"Lunch menu",
            "type":"PERCENTAGE","amount":20
            """;

    @Mock
    private PromocionService promocionService;

    @InjectMocks
    private PromocionController promocionController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(promocionController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @ParameterizedTest
    @MethodSource("incompleteRequests")
    void returns400ForIncompleteInput(String body) throws Exception {
        mockMvc.perform(post("/api/promociones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(promocionService);
    }

    static Stream<String> incompleteRequests() {
        String validBody = "{" + VALID_FIELDS + ",\"maxUses\":1}";
        return Stream.concat(
                Stream.of(validBody.replace("\"restaurantId\":1,", ""),
                        validBody.replace("\"restaurantId\":1", "\"restaurantId\":null")),
                Stream.of("\"name\":\"Lunch promotion\"", "\"applicableProducts\":\"Lunch menu\"",
                                "\"type\":\"PERCENTAGE\"")
                        .flatMap(field -> {
                            String key = field.substring(0, field.indexOf(':') + 1);
                            return Stream.of(validBody.replace(field + ",", ""),
                                    validBody.replace(field, key + "null"),
                                    validBody.replace(field, key + "\"\""),
                                    validBody.replace(field, key + "\"   \""));
                        }));
    }

    @ParameterizedTest
    @CsvSource({"maxUses,-1", "maxUses,0", "maxUseAmountPerUser,-1", "maxUseAmountPerUser,0"})
    void returns400ForLimitsBelowOne(String field, int limit) throws Exception {
        String body = "{" + VALID_FIELDS + ",\"endTimestamp\":\"2026-10-30T13:00:00\",\""
                + field + "\":" + limit + "}";
        String message = field.equals("maxUses")
                ? "El límite de usos debe ser mayor a 0."
                : "El límite de usos por usuario debe ser mayor a 0.";

        mockMvc.perform(post("/api/promociones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(message));

        verifyNoInteractions(promocionService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-10-30T11:00:00", "2026-10-30T12:00:00"})
    void returns400ForEndAtOrBeforeStart(String end) throws Exception {
        String body = "{" + VALID_FIELDS + ",\"maxUses\":1,\"startTimestamp\":\"2026-10-30T12:00:00\","
                + "\"endTimestamp\":\"" + end + "\"}";

        mockMvc.perform(post("/api/promociones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("La fecha de inicio debe ser anterior a la fecha de vencimiento."));

        verifyNoInteractions(promocionService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{", "{}"})
    void returns400ForEmptyOrMalformedBody(String body) throws Exception {
        mockMvc.perform(post("/api/promociones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(promocionService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\"maxUses\":1,\"maxUseAmountPerUser\":1",
            "\"endTimestamp\":\"2026-10-30T13:00:00\"",
            "\"startTimestamp\":\"2026-10-30T12:00:00\",\"maxUses\":1"
    })
    void returns201ForValidOptionalDateAndLimitCombinations(String bounds) throws Exception {
        when(promocionService.create(any(PromocionRequest.class)))
                .thenReturn(PromocionResponse.builder().discountId(10).status("Active").build());

        mockMvc.perform(post("/api/promociones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + VALID_FIELDS + "," + bounds + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.discountId").value(10))
                .andExpect(jsonPath("$.status").value("Active"));

        verify(promocionService).create(any(PromocionRequest.class));
    }

    @Test
    void returns201AndPassesValidDatesAndLimitsToService() throws Exception {
        when(promocionService.create(any(PromocionRequest.class)))
                .thenReturn(PromocionResponse.builder().discountId(10).status("Active").build());
        String body = "{" + VALID_FIELDS + ",\"maxUses\":1,\"maxUseAmountPerUser\":1,"
                + "\"startTimestamp\":\"2026-10-30T12:00:00\",\"endTimestamp\":\"2026-10-30T13:00:00\"}";

        mockMvc.perform(post("/api/promociones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        ArgumentCaptor<PromocionRequest> captor = ArgumentCaptor.forClass(PromocionRequest.class);
        verify(promocionService).create(captor.capture());
        assertEquals(1, captor.getValue().getMaxUses());
        assertEquals(1, captor.getValue().getMaxUseAmountPerUser());
        assertEquals(LocalDateTime.of(2026, 10, 30, 12, 0), captor.getValue().getStartTimestamp());
        assertEquals(LocalDateTime.of(2026, 10, 30, 13, 0), captor.getValue().getEndTimestamp());
    }
}
