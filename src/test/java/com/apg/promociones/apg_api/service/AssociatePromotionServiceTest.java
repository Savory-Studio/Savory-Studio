package com.apg.promociones.apg_api.service;

import com.apg.promociones.apg_api.dto.AssociatePromotionRequest;
import com.apg.promociones.apg_api.dto.AssociatePromotionResponse;
import com.apg.promociones.apg_api.dto.UserCouponResponse;
import com.apg.promociones.apg_api.exception.BadRequestException;
import com.apg.promociones.apg_api.exception.BusinessRuleException;
import com.apg.promociones.apg_api.exception.ResourceNotFoundException;
import com.apg.promociones.apg_api.model.Coupon;
import com.apg.promociones.apg_api.model.Discount;
import com.apg.promociones.apg_api.model.Restaurant;
import com.apg.promociones.apg_api.model.User;
import com.apg.promociones.apg_api.model.enums.CouponStatus;
import com.apg.promociones.apg_api.model.enums.DiscountStatus;
import com.apg.promociones.apg_api.model.enums.DiscountType;
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
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    private Restaurant restaurant;
    private Discount discount;
    private AssociatePromotionRequest request;

    @BeforeEach
    void setUp() {
        user = User.builder().id(10).firstName("Ana").lastName("Perez").build();
        restaurant = Restaurant.builder().id(1).name("Savory Grill").owner(user).build();
        discount = Discount.builder()
                .id(20)
                .restaurant(restaurant)
                .name("2x1 lunch")
                .type(DiscountType.IXJ)
                .status(DiscountStatus.Active)
                .startTimestamp(LocalDateTime.now().minusDays(1))
                .endTimestamp(LocalDateTime.now().plusDays(7))
                .build();
        request = new AssociatePromotionRequest(20);
    }

    @Test
    void getUserCouponsReturnsMappedCouponsForExistingUser() {
        Coupon coupon = Coupon.builder()
                .id(15)
                .user(user)
                .discount(discount)
                .status(CouponStatus.UNUSED)
                .couponHash("hash-123")
                .claimTimestamp(LocalDateTime.of(2026, 10, 30, 14, 0))
                .build();

        when(userRepository.findById(10)).thenReturn(Optional.of(user));
        when(couponRepository.findByUser_Id(10)).thenReturn(List.of(coupon));

        List<UserCouponResponse> result = associatePromotionService.getUserCoupons(10);

        assertEquals(1, result.size());
        assertEquals(15, result.get(0).getCouponId());
        assertEquals(10, result.get(0).getUserId());
        assertEquals(20, result.get(0).getPromotionId());
        assertEquals("2x1 lunch", result.get(0).getPromotionName());
        assertEquals(1, result.get(0).getRestaurantId());
        assertEquals("Savory Grill", result.get(0).getRestaurantName());
        assertEquals("hash-123", result.get(0).getCouponHash());
        assertEquals("UNUSED", result.get(0).getStatus());
    }

    @Test
    void getUserCouponsReturnsEmptyListWhenUserHasNoCoupons() {
        when(userRepository.findById(10)).thenReturn(Optional.of(user));
        when(couponRepository.findByUser_Id(10)).thenReturn(List.of());

        List<UserCouponResponse> result = associatePromotionService.getUserCoupons(10);

        assertTrue(result.isEmpty());
    }

    @Test
    void getUserCouponsThrows400WhenUserIdIsNonPositive() {
        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> associatePromotionService.getUserCoupons(0));

        assertEquals("The userId must be a positive integer.", ex.getMessage());
        verify(couponRepository, never()).findByUser_Id(any());
    }

    @Test
    void getUserCouponsThrows404WhenUserDoesNotExist() {
        when(userRepository.findById(999)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> associatePromotionService.getUserCoupons(999));

        assertEquals("User not found.", ex.getMessage());
        verify(couponRepository, never()).findByUser_Id(any());
    }

    @Test
    void associatesCurrentPromotionAndReturnsUnusedCoupon() {
        when(userRepository.findById(10)).thenReturn(Optional.of(user));
        when(discountRepository.findById(20)).thenReturn(Optional.of(discount));
        when(couponRepository.existsUnusedByUserAndDiscount(10, 20)).thenReturn(false);
        when(couponRepository.saveAndFlush(any(Coupon.class))).thenAnswer(invocation -> {
            Coupon coupon = invocation.getArgument(0);
            coupon.setId(99);
            return coupon;
        });

        AssociatePromotionResponse response = associatePromotionService.associate(10, request);

        assertEquals(99, response.getCouponId());
        assertEquals(10, response.getUserId());
        assertEquals(20, response.getPromotionId());
        assertEquals("UNUSED", response.getStatus());
        assertEquals("Promoción guardada en el perfil del cliente.", response.getMessage());

        ArgumentCaptor<Coupon> captor = ArgumentCaptor.forClass(Coupon.class);
        verify(couponRepository).saveAndFlush(captor.capture());
        assertEquals(CouponStatus.UNUSED, captor.getValue().getStatus());
        assertEquals(user, captor.getValue().getUser());
        assertEquals(discount, captor.getValue().getDiscount());
    }

    @Test
    void throws404WhenUserDoesNotExist() {
        when(userRepository.findById(10)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> associatePromotionService.associate(10, request));

        assertEquals("User not found.", ex.getMessage());
        verify(couponRepository, never()).saveAndFlush(any());
    }

    @Test
    void throws404WhenPromotionDoesNotExist() {
        when(userRepository.findById(10)).thenReturn(Optional.of(user));
        when(discountRepository.findById(20)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> associatePromotionService.associate(10, request));

        assertEquals("Promotion not found.", ex.getMessage());
        verify(couponRepository, never()).saveAndFlush(any());
    }

    @Test
    void throws409WhenPromotionIsNotActive() {
        discount.setStatus(DiscountStatus.Suspended);
        when(userRepository.findById(10)).thenReturn(Optional.of(user));
        when(discountRepository.findById(20)).thenReturn(Optional.of(discount));

        BusinessRuleException ex = assertThrows(
                BusinessRuleException.class,
                () -> associatePromotionService.associate(10, request));

        assertEquals("This promotion is no longer available.", ex.getMessage());
        verify(couponRepository, never()).saveAndFlush(any());
    }

    @Test
    void throws409WhenPromotionHasExpired() {
        discount.setEndTimestamp(LocalDateTime.now().minusMinutes(1));
        when(userRepository.findById(10)).thenReturn(Optional.of(user));
        when(discountRepository.findById(20)).thenReturn(Optional.of(discount));

        BusinessRuleException ex = assertThrows(
                BusinessRuleException.class,
                () -> associatePromotionService.associate(10, request));

        assertEquals("This promotion has expired.", ex.getMessage());
        verify(couponRepository, never()).saveAndFlush(any());
    }

    @Test
    void throws409WhenPromotionHasNotStarted() {
        discount.setStartTimestamp(LocalDateTime.now().plusDays(1));
        when(userRepository.findById(10)).thenReturn(Optional.of(user));
        when(discountRepository.findById(20)).thenReturn(Optional.of(discount));

        BusinessRuleException ex = assertThrows(
                BusinessRuleException.class,
                () -> associatePromotionService.associate(10, request));

        assertEquals("This promotion is not active yet.", ex.getMessage());
        verify(couponRepository, never()).saveAndFlush(any());
    }

    @Test
    void throws409WhenAlreadyAssociatedUnused() {
        when(userRepository.findById(10)).thenReturn(Optional.of(user));
        when(discountRepository.findById(20)).thenReturn(Optional.of(discount));
        when(couponRepository.existsUnusedByUserAndDiscount(10, 20)).thenReturn(true);

        BusinessRuleException ex = assertThrows(
                BusinessRuleException.class,
                () -> associatePromotionService.associate(10, request));

        assertEquals("This promotion is already associated with the customer profile.", ex.getMessage());
        verify(couponRepository, never()).saveAndFlush(any());
    }

    @Test
    void throws409WhenConcurrentInsertViolatesUniqueIndex() {
        when(userRepository.findById(10)).thenReturn(Optional.of(user));
        when(discountRepository.findById(20)).thenReturn(Optional.of(discount));
        when(couponRepository.existsUnusedByUserAndDiscount(10, 20)).thenReturn(false);
        when(couponRepository.saveAndFlush(any(Coupon.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        BusinessRuleException ex = assertThrows(
                BusinessRuleException.class,
                () -> associatePromotionService.associate(10, request));

        assertEquals("This promotion is already associated with the customer profile.", ex.getMessage());
    }
}
