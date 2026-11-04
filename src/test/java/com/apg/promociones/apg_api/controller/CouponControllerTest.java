package com.apg.promociones.apg_api.controller;

import com.apg.promociones.apg_api.dto.AssociatePromotionRequest;
import com.apg.promociones.apg_api.dto.AssociatePromotionResponse;
import com.apg.promociones.apg_api.exception.BusinessRuleException;
import com.apg.promociones.apg_api.exception.GlobalExceptionHandler;
import com.apg.promociones.apg_api.exception.ResourceNotFoundException;
import com.apg.promociones.apg_api.service.AssociatePromotionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CouponControllerTest {

    @Mock
    private AssociatePromotionService associatePromotionService;

    @InjectMocks
    private CouponController couponController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(couponController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
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
                        .content("{\"promotionId\":20}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("The X-User-Id header is missing."));
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
}
