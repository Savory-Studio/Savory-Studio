package com.apg.promociones.apg_api.service;

import com.apg.promociones.apg_api.dto.AssociatePromotionRequest;
import com.apg.promociones.apg_api.dto.AssociatePromotionResponse;
import com.apg.promociones.apg_api.exception.BusinessRuleException;
import com.apg.promociones.apg_api.exception.ResourceNotFoundException;
import com.apg.promociones.apg_api.model.Coupon;
import com.apg.promociones.apg_api.model.Discount;
import com.apg.promociones.apg_api.model.User;
import com.apg.promociones.apg_api.model.enums.CouponStatus;
import com.apg.promociones.apg_api.model.enums.DiscountStatus;
import com.apg.promociones.apg_api.repository.CouponRepository;
import com.apg.promociones.apg_api.repository.DiscountRepository;
import com.apg.promociones.apg_api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssociatePromotionServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private DiscountRepository discountRepository;

    @Mock
    private CouponRepository couponRepository;

    @InjectMocks
    private AssociatePromotionService associatePromotionService;

    private User user;
    private Discount discount;
    private AssociatePromotionRequest request;

    @BeforeEach
    void setUp() {
        user = User.builder().id(10).firstName("Ana").lastName("Perez").build();
        discount = Discount.builder()
                .id(20)
                .name("2x1 lunch")
                .status(DiscountStatus.Active)
                .startTimestamp(LocalDateTime.now().minusDays(1))
                .endTimestamp(LocalDateTime.now().plusDays(7))
                .build();
        request = new AssociatePromotionRequest(10, 20);
    }

    @Test
    void associatesCurrentPromotionAndReturnsUnusedCoupon() {
        when(userRepository.findById(10)).thenReturn(Optional.of(user));
        when(discountRepository.findById(20)).thenReturn(Optional.of(discount));
        when(couponRepository.existsUnusedByUserAndDiscount(10, 20)).thenReturn(false);
        when(couponRepository.save(any(Coupon.class))).thenAnswer(invocation -> {
            Coupon coupon = invocation.getArgument(0);
            coupon.setId(99);
            return coupon;
        });

        AssociatePromotionResponse response = associatePromotionService.associate(request);

        assertEquals(99, response.getCouponId());
        assertEquals(10, response.getUserId());
        assertEquals(20, response.getPromotionId());
        assertEquals("UNUSED", response.getStatus());
        assertEquals("Promotion associated with the customer profile.", response.getMessage());

        ArgumentCaptor<Coupon> captor = ArgumentCaptor.forClass(Coupon.class);
        verify(couponRepository).save(captor.capture());
        assertEquals(CouponStatus.UNUSED, captor.getValue().getStatus());
        assertEquals(user, captor.getValue().getUser());
        assertEquals(discount, captor.getValue().getDiscount());
    }

    @Test
    void throws404WhenUserDoesNotExist() {
        when(userRepository.findById(10)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> associatePromotionService.associate(request));

        assertEquals("User not found.", ex.getMessage());
        verify(couponRepository, never()).save(any());
    }

    @Test
    void throws404WhenPromotionDoesNotExist() {
        when(userRepository.findById(10)).thenReturn(Optional.of(user));
        when(discountRepository.findById(20)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> associatePromotionService.associate(request));

        assertEquals("Promotion not found.", ex.getMessage());
        verify(couponRepository, never()).save(any());
    }

    @Test
    void throws409WhenPromotionIsNotActive() {
        discount.setStatus(DiscountStatus.Suspended);
        when(userRepository.findById(10)).thenReturn(Optional.of(user));
        when(discountRepository.findById(20)).thenReturn(Optional.of(discount));

        BusinessRuleException ex = assertThrows(
                BusinessRuleException.class,
                () -> associatePromotionService.associate(request));

        assertEquals("This promotion is no longer available.", ex.getMessage());
        verify(couponRepository, never()).save(any());
    }

    @Test
    void throws409WhenPromotionHasExpired() {
        discount.setEndTimestamp(LocalDateTime.now().minusMinutes(1));
        when(userRepository.findById(10)).thenReturn(Optional.of(user));
        when(discountRepository.findById(20)).thenReturn(Optional.of(discount));

        BusinessRuleException ex = assertThrows(
                BusinessRuleException.class,
                () -> associatePromotionService.associate(request));

        assertEquals("This promotion has expired.", ex.getMessage());
        verify(couponRepository, never()).save(any());
    }

    @Test
    void throws409WhenPromotionHasNotStarted() {
        discount.setStartTimestamp(LocalDateTime.now().plusDays(1));
        when(userRepository.findById(10)).thenReturn(Optional.of(user));
        when(discountRepository.findById(20)).thenReturn(Optional.of(discount));

        BusinessRuleException ex = assertThrows(
                BusinessRuleException.class,
                () -> associatePromotionService.associate(request));

        assertEquals("This promotion is not active yet.", ex.getMessage());
        verify(couponRepository, never()).save(any());
    }

    @Test
    void throws409WhenAlreadyAssociatedUnused() {
        when(userRepository.findById(10)).thenReturn(Optional.of(user));
        when(discountRepository.findById(20)).thenReturn(Optional.of(discount));
        when(couponRepository.existsUnusedByUserAndDiscount(10, 20)).thenReturn(true);

        BusinessRuleException ex = assertThrows(
                BusinessRuleException.class,
                () -> associatePromotionService.associate(request));

        assertEquals("This promotion is already associated with the customer profile.", ex.getMessage());
        verify(couponRepository, never()).save(any());
    }
}
