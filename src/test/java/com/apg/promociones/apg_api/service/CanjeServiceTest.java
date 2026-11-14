package com.apg.promociones.apg_api.service;

import com.apg.promociones.apg_api.dto.CanjeRequest;
import com.apg.promociones.apg_api.dto.CanjeResponse;
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
import com.apg.promociones.apg_api.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CanjeServiceTest {

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private DiscountRepository discountRepository;

    @InjectMocks
    private CanjeService canjeService;

    private User owner;
    private User customer;
    private Discount discount;
    private Coupon coupon;

    @BeforeEach
    void setUp() {
        owner = User.builder().id(1).firstName("Owner").lastName("Test").build();
        customer = User.builder().id(10).firstName("Ana").lastName("Perez").build();
        Restaurant restaurant = Restaurant.builder().id(1).name("Savory Grill").owner(owner).build();
        discount = Discount.builder()
                .id(20)
                .restaurant(restaurant)
                .name("2x1 lunch")
                .type(DiscountType.IXJ)
                .status(DiscountStatus.Active)
                .startTimestamp(LocalDateTime.now().minusDays(1))
                .endTimestamp(LocalDateTime.now().plusDays(7))
                .build();
        coupon = Coupon.builder()
                .id(4)
                .user(customer)
                .discount(discount)
                .status(CouponStatus.UNUSED)
                .couponHash("3060c1f8-a7ee-4fac-a7a7-c8d27ec5fa3a")
                .claimTimestamp(LocalDateTime.now().minusDays(1))
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void locksCouponThenDiscountBeforeCheckingRulesAndSavingByCouponId() {
        stubLockedLookupById();
        when(couponRepository.saveAndFlush(any(Coupon.class))).thenAnswer(inv -> inv.getArgument(0));

        CanjeResponse response = canjeService.redeem(requestById(4));

        InOrder order = inOrder(couponRepository, discountRepository);
        order.verify(couponRepository).findByIdForUpdate(4);
        order.verify(discountRepository).findByIdForUpdate(20);
        order.verify(couponRepository).saveAndFlush(coupon);
        verify(couponRepository, never()).findById(any());
        verify(couponRepository, never()).findByCouponHash(any());

        assertEquals("USED", response.getStatus());
        assertEquals(4, response.getCouponId());
        assertEquals(CouponStatus.USED, coupon.getStatus());
        assertNotNull(coupon.getRedemptionTimestamp());
        assertEquals(coupon.getRedemptionTimestamp(), response.getRedemptionTimestamp());
    }

    @Test
    void locksCouponByHashWhenHashIsProvidedAndGivesItPriorityOverId() {
        when(couponRepository.findByCouponHashForUpdate(coupon.getCouponHash())).thenReturn(Optional.of(coupon));
        when(discountRepository.findByIdForUpdate(20)).thenReturn(Optional.of(discount));
        when(couponRepository.saveAndFlush(any(Coupon.class))).thenAnswer(inv -> inv.getArgument(0));

        CanjeRequest request = new CanjeRequest(999, coupon.getCouponHash());
        CanjeResponse response = canjeService.redeem(request);

        verify(couponRepository).findByCouponHashForUpdate(coupon.getCouponHash());
        verify(couponRepository, never()).findByIdForUpdate(any());
        assertEquals(coupon.getCouponHash(), response.getCouponHash());
    }

    @Test
    void throws400WhenNeitherCouponIdNorHashIsProvided() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> canjeService.redeem(new CanjeRequest(null, null)));

        assertEquals("Debe proporcionar couponId o couponHash.", ex.getMessage());
        verify(couponRepository, never()).findByIdForUpdate(any());
        verify(couponRepository, never()).findByCouponHashForUpdate(any());
    }

    @Test
    void throws404WhenCouponDoesNotExist() {
        when(couponRepository.findByIdForUpdate(999)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> canjeService.redeem(requestById(999)));

        assertEquals("Cupón no encontrado.", ex.getMessage());
        verify(discountRepository, never()).findByIdForUpdate(any());
        verify(couponRepository, never()).saveAndFlush(any());
    }

    @Test
    void rn01RejectsCouponThatIsAlreadyUsedWithoutWriting() {
        coupon.setStatus(CouponStatus.USED);
        LocalDateTime originalRedemption = LocalDateTime.now().minusMinutes(5);
        coupon.setRedemptionTimestamp(originalRedemption);
        stubLockedLookupById();

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> canjeService.redeem(requestById(4)));

        assertEquals("Este cupón ya fue utilizado.", ex.getMessage());
        assertEquals(originalRedemption, coupon.getRedemptionTimestamp());
        verify(couponRepository, never()).saveAndFlush(any());
    }

    @Test
    void rn02RejectsInactivePromotions() {
        for (DiscountStatus inactive : List.of(DiscountStatus.Finished, DiscountStatus.Suspended)) {
            discount.setStatus(inactive);
            stubLockedLookupById();

            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> canjeService.redeem(requestById(4)));

            assertEquals("Esta promoción ya no está disponible.", ex.getMessage());
        }
        verify(couponRepository, never()).saveAndFlush(any());
    }

    @Test
    void rn03RejectsExpiredPromotion() {
        discount.setEndTimestamp(LocalDateTime.now().minusMinutes(1));
        stubLockedLookupById();

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> canjeService.redeem(requestById(4)));

        assertEquals("Esta promoción ha vencido.", ex.getMessage());
        verify(couponRepository, never()).saveAndFlush(any());
    }

    @Test
    void rn04RejectsWhenUserReachedTheirLimit() {
        discount.setMaxUseAmountPerUser(2);
        stubLockedLookupById();
        when(couponRepository.countByUserAndDiscountAndStatus(customer, discount, CouponStatus.USED)).thenReturn(2L);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> canjeService.redeem(requestById(4)));

        assertEquals("Has alcanzado el límite de usos de esta promoción.", ex.getMessage());
        assertEquals(CouponStatus.UNUSED, coupon.getStatus());
        verify(couponRepository, never()).saveAndFlush(any());
    }

    @Test
    void rn04AllowsRedemptionWhileBelowUserLimit() {
        discount.setMaxUseAmountPerUser(2);
        stubLockedLookupById();
        when(couponRepository.countByUserAndDiscountAndStatus(customer, discount, CouponStatus.USED)).thenReturn(1L);
        when(couponRepository.saveAndFlush(any(Coupon.class))).thenAnswer(inv -> inv.getArgument(0));

        assertEquals("USED", canjeService.redeem(requestById(4)).getStatus());
    }

    @Test
    void rn05RejectsWhenGlobalLimitIsReached() {
        discount.setMaxUses(10);
        stubLockedLookupById();
        when(couponRepository.countByDiscountAndStatus(discount, CouponStatus.USED)).thenReturn(10L);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> canjeService.redeem(requestById(4)));

        assertEquals("Esta promoción se ha agotado.", ex.getMessage());
        assertEquals(CouponStatus.UNUSED, coupon.getStatus());
        verify(couponRepository, never()).saveAndFlush(any());
    }

    @Test
    void rn05AllowsTheLastAvailableUse() {
        discount.setMaxUses(10);
        stubLockedLookupById();
        when(couponRepository.countByDiscountAndStatus(discount, CouponStatus.USED)).thenReturn(9L);
        when(couponRepository.saveAndFlush(any(Coupon.class))).thenAnswer(inv -> inv.getArgument(0));

        assertEquals("USED", canjeService.redeem(requestById(4)).getStatus());
    }

    @Test
    void contextualAuthorizationRejectsUserWhoDoesNotOwnTheRestaurant() {
        authenticateAs(99);
        stubLockedLookupById();

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> canjeService.redeem(requestById(4)));

        assertEquals("Usuario no tiene permiso para canjear cupones de este restaurante.", ex.getMessage());
        assertEquals(CouponStatus.UNUSED, coupon.getStatus());
        verify(couponRepository, never()).saveAndFlush(any());
    }

    @Test
    void contextualAuthorizationAllowsTheRestaurantOwner() {
        authenticateAs(owner.getId());
        stubLockedLookupById();
        when(couponRepository.saveAndFlush(any(Coupon.class))).thenAnswer(inv -> inv.getArgument(0));

        assertEquals("USED", canjeService.redeem(requestById(4)).getStatus());
    }

    @Test
    void mapsPessimisticLockingFailureTo409Conflict() {
        var handler = new com.apg.promociones.apg_api.exception.GlobalExceptionHandler();
        var errorResponse = handler.handleConcurrencyConflict(
                new org.springframework.dao.PessimisticLockingFailureException("Could not obtain lock"));

        assertEquals(409, errorResponse.getStatus());
        assertEquals("No se pudo completar la operación por otra petición concurrente. Intente nuevamente.",
                errorResponse.getMessage());
    }

    private void stubLockedLookupById() {
        when(couponRepository.findByIdForUpdate(4)).thenReturn(Optional.of(coupon));
        when(discountRepository.findByIdForUpdate(20)).thenReturn(Optional.of(discount));
    }

    private static CanjeRequest requestById(Integer couponId) {
        return new CanjeRequest(couponId, null);
    }

    private static void authenticateAs(Integer userId) {
        UserPrincipal principal = new UserPrincipal(userId, "user-" + userId + "@example.test", null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "token", principal.getAuthorities()));
    }
}
