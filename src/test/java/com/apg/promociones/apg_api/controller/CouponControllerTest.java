package com.apg.promociones.apg_api.controller;

import com.apg.promociones.apg_api.dto.AssociatePromotionRequest;
import com.apg.promociones.apg_api.dto.AssociatePromotionResponse;
import com.apg.promociones.apg_api.dto.UserCouponResponse;
import com.apg.promociones.apg_api.exception.BusinessRuleException;
import com.apg.promociones.apg_api.exception.GlobalExceptionHandler;
import com.apg.promociones.apg_api.exception.ResourceNotFoundException;
import com.apg.promociones.apg_api.security.SimulatedAuthFilter;
import com.apg.promociones.apg_api.security.SimulatedAuthProperties;
import com.apg.promociones.apg_api.service.AssociatePromotionService;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CouponControllerTest {

    @Mock
    private AssociatePromotionService associatePromotionService;

    private final SimulatedAuthProperties authProperties = new SimulatedAuthProperties();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authProperties.setToken("coupon-controller-test");
        mockMvc = MockMvcBuilders.standaloneSetup(new CouponController(associatePromotionService, authProperties))
                .addFilters(new SimulatedAuthFilter(authProperties))
                .defaultRequest(get("/").header(HttpHeaders.AUTHORIZATION, "Bearer coupon-controller-test"))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returns200WithUserCouponsList() throws Exception {
        UserCouponResponse coupon = UserCouponResponse.builder()
                .couponId(15)
                .userId(10)
                .promotionId(20)
                .promotionName("2x1 Lunch")
                .restaurantId(1)
                .restaurantName("Savory Grill")
                .couponHash("a3f1b2c4-d5e6-7890-abcd-ef1234567890")
                .status("UNUSED")
                .claimTimestamp(LocalDateTime.of(2026, 10, 30, 14, 35))
                .build();

        when(associatePromotionService.getUserCoupons(10)).thenReturn(List.of(coupon));

        mockMvc.perform(get("/api/cupones/usuario/10")
                        .header("X-User-Id", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].couponId").value(15))
                .andExpect(jsonPath("$[0].userId").value(10))
                .andExpect(jsonPath("$[0].promotionId").value(20))
                .andExpect(jsonPath("$[0].promotionName").value("2x1 Lunch"))
                .andExpect(jsonPath("$[0].couponHash").value("a3f1b2c4-d5e6-7890-abcd-ef1234567890"))
                .andExpect(jsonPath("$[0].status").value("UNUSED"));
    }

    @Test
    void returns404WhenUserDoesNotExistOnGetCoupons() throws Exception {
        when(associatePromotionService.getUserCoupons(999))
                .thenThrow(new ResourceNotFoundException("User not found."));

        mockMvc.perform(get("/api/cupones/usuario/999")
                        .header("X-User-Id", "999")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("User not found."));
    }

    @Test
    void returns400WhenUserIdIsInvalidOnGetCoupons() throws Exception {
        mockMvc.perform(get("/api/cupones/usuario/0")
                        .header("X-User-Id", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("The userId must be a positive integer."));
    }

    @Test
    void returns201WhenAssociationSucceeds() throws Exception {
        when(associatePromotionService.associate(any(), any(AssociatePromotionRequest.class)))
                .thenReturn(AssociatePromotionResponse.builder()
                        .couponId(99)
                        .userId(10)
                        .promotionId(20)
                        .status("UNUSED")
                        .claimTimestamp(LocalDateTime.of(2026, 10, 26, 16, 0))
                        .message("Promotion associated with the customer profile.")
                        .build());

        mockMvc.perform(post("/api/coupons")
                        .header("X-User-Id", "10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"promotionId\":20}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.couponId").value(99))
                .andExpect(jsonPath("$.userId").value(10))
                .andExpect(jsonPath("$.promotionId").value(20))
                .andExpect(jsonPath("$.status").value("UNUSED"));
    }

    @Test
    void returns401WhenUserIdHeaderIsMissing() throws Exception {
        mockMvc.perform(post("/api/coupons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"promotionId\":20,\"userId\":10}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("The X-User-Id header is missing or invalid."));
    }

    @Test
    void returns400WhenFieldsAreMissing() throws Exception {
        mockMvc.perform(post("/api/coupons")
                        .header("X-User-Id", "10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns404WhenPromotionDoesNotExist() throws Exception {
        when(associatePromotionService.associate(any(), any(AssociatePromotionRequest.class)))
                .thenThrow(new ResourceNotFoundException("Promotion not found."));

        mockMvc.perform(post("/api/coupons")
                        .header("X-User-Id", "10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"promotionId\":999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Promotion not found."));
    }

    @Test
    void returns409WhenPromotionIsNotCurrent() throws Exception {
        when(associatePromotionService.associate(any(), any(AssociatePromotionRequest.class)))
                .thenThrow(new BusinessRuleException("This promotion has expired."));

        mockMvc.perform(post("/api/coupons")
                        .header("X-User-Id", "10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"promotionId\":20}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This promotion has expired."));
    }

    @Test
    void returns404WhenUserFromHeaderDoesNotExist() throws Exception {
        when(associatePromotionService.associate(eq(999), any(AssociatePromotionRequest.class)))
                .thenThrow(new ResourceNotFoundException("User not found."));

        mockMvc.perform(post("/api/coupons")
                        .header("X-User-Id", "999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"promotionId\":20}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found."));
    }

    @Test
    void returns404WhenBodyUserDoesNotExistAndSimulationIsDisabled() throws Exception {
        authProperties.setEnabled(false);
        when(associatePromotionService.associate(eq(999), any(AssociatePromotionRequest.class)))
                .thenThrow(new ResourceNotFoundException("User not found."));

        mockMvc.perform(post("/api/coupons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":999,\"promotionId\":20}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found."));
    }

    @Test
    void refusesToClaimForAnotherUser() throws Exception {
        mockMvc.perform(post("/api/coupons")
                        .header("X-User-Id", "10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":15,\"promotionId\":20}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        verifyNoInteractions(associatePromotionService);
    }

    @Test
    void refusesToListAnotherUsersCouponsThroughBothAliases() throws Exception {
        for (String path : List.of("/api/cupones/usuario/15", "/api/coupons/usuario/15")) {
            mockMvc.perform(get(path).header("X-User-Id", "10"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
        }
        verifyNoInteractions(associatePromotionService);
    }
}
