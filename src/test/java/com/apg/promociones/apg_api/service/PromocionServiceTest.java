package com.apg.promociones.apg_api.service;

import com.apg.promociones.apg_api.dto.ActivePromotionResponse;
import com.apg.promociones.apg_api.dto.PromocionRequest;
import com.apg.promociones.apg_api.exception.UnauthorizedException;
import com.apg.promociones.apg_api.model.Discount;
import com.apg.promociones.apg_api.model.Restaurant;
import com.apg.promociones.apg_api.model.User;
import com.apg.promociones.apg_api.model.enums.DiscountStatus;
import com.apg.promociones.apg_api.model.enums.DiscountType;
import com.apg.promociones.apg_api.repository.DiscountRepository;
import com.apg.promociones.apg_api.repository.RestaurantRepository;
import com.apg.promociones.apg_api.repository.RestaurantStaffRepository;
import com.apg.promociones.apg_api.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromocionServiceTest {

    @Mock
    private DiscountRepository discountRepository;

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private RestaurantStaffRepository restaurantStaffRepository;

    private PromocionService promocionService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        promocionService = new PromocionService(discountRepository, restaurantRepository, restaurantStaffRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @EnumSource(DiscountType.class)
    void createPersistsOnlyTheValuesForThePromotionType(DiscountType type) {
        PromocionRequest request = new PromocionRequest();
        request.setRestaurantId(1);
        request.setName("Lunch offer");
        request.setApplicableProducts("Lunch");
        request.setType(type.name());
        request.setAmount(BigDecimal.TEN);
        request.setCustomerTakesAmount(2);
        request.setCustomerPaysAmount(1);
        request.setMaxUses(5);
        when(restaurantRepository.findById(1)).thenReturn(Optional.of(Restaurant.builder().id(1).build()));

        promocionService.create(request);

        ArgumentCaptor<Discount> saved = ArgumentCaptor.forClass(Discount.class);
        verify(discountRepository).save(saved.capture());
        Discount discount = saved.getValue();
        if (type == DiscountType.IXJ) {
            assertNull(discount.getAmount());
            assertEquals(2, discount.getCustomerTakesAmount());
            assertEquals(1, discount.getCustomerPaysAmount());
        } else {
            assertEquals(BigDecimal.TEN, discount.getAmount());
            assertNull(discount.getCustomerTakesAmount());
            assertNull(discount.getCustomerPaysAmount());
        }
    }

    @Test
    void getActivePromotionsMapsDiscountsToResponses() {
        Restaurant restaurant = Restaurant.builder()
                .id(1)
                .name("Savory Grill")
                .build();

        Discount discount = Discount.builder()
                .id(5)
                .restaurant(restaurant)
                .name("Lunch 20% off")
                .applicableProducts("Executive menu")
                .type(DiscountType.PERCENTAGE)
                .amount(new BigDecimal("20.00"))
                .status(DiscountStatus.Active)
                .startTimestamp(LocalDateTime.of(2026, 10, 26, 8, 0))
                .endTimestamp(LocalDateTime.of(2026, 11, 26, 23, 59))
                .maxUses(100)
                .maxUseAmountPerUser(2)
                .imageUrl("https://example.com/promo.jpg")
                .additionalUseConditions("Lunch hours only")
                .build();

        when(discountRepository.findActivePromotions()).thenReturn(List.of(discount));

        List<ActivePromotionResponse> result = promocionService.getActivePromotions();

        assertEquals(1, result.size());
        ActivePromotionResponse item = result.get(0);
        assertEquals(5, item.getDiscountId());
        assertEquals(1, item.getRestaurantId());
        assertEquals("Savory Grill", item.getRestaurantName());
        assertEquals("Lunch 20% off", item.getName());
        assertEquals("Executive menu", item.getApplicableProducts());
        assertEquals("PERCENTAGE", item.getType());
        assertEquals(new BigDecimal("20.00"), item.getAmount());
        assertEquals("Active", item.getStatus());
        assertEquals(100, item.getMaxUses());
        assertEquals(2, item.getMaxUseAmountPerUser());
        verify(discountRepository).findActivePromotions();
    }

    @Test
    void getActivePromotionsReturnsEmptyListWhenNoneAvailable() {
        when(discountRepository.findActivePromotions()).thenReturn(List.of());

        List<ActivePromotionResponse> result = promocionService.getActivePromotions();

        assertTrue(result.isEmpty());
        verify(discountRepository).findActivePromotions();
    }

    @Test
    void createAllowsRestaurantOwner() {
        authenticateAs(10, "owner@example.com");
        Restaurant restaurant = Restaurant.builder()
                .id(1)
                .name("Owner Bistro")
                .owner(User.builder().id(10).build())
                .build();
        when(restaurantRepository.findById(1)).thenReturn(Optional.of(restaurant));

        promocionService.create(validPercentageRequest(1));

        verify(discountRepository).save(any(Discount.class));
    }

    @Test
    void createAllowsAuthorizedRestaurantStaffPerRest05() {
        authenticateAs(25, "staff@example.com");
        Restaurant restaurant = Restaurant.builder()
                .id(1)
                .name("Owner Bistro")
                .owner(User.builder().id(10).build())
                .build();
        when(restaurantRepository.findById(1)).thenReturn(Optional.of(restaurant));
        when(restaurantStaffRepository.existsByUserIdAndRestaurantId(25, 1)).thenReturn(true);

        promocionService.create(validPercentageRequest(1));

        verify(restaurantStaffRepository).existsByUserIdAndRestaurantId(25, 1);
        verify(discountRepository).save(any(Discount.class));
    }

    @Test
    void createRejectsUserWhoIsNeitherOwnerNorStaff() {
        authenticateAs(99, "outsider@example.com");
        Restaurant restaurant = Restaurant.builder()
                .id(1)
                .name("Owner Bistro")
                .owner(User.builder().id(10).build())
                .build();
        when(restaurantRepository.findById(1)).thenReturn(Optional.of(restaurant));
        when(restaurantStaffRepository.existsByUserIdAndRestaurantId(99, 1)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> promocionService.create(validPercentageRequest(1)));
        verify(discountRepository, never()).save(any());
    }

    @Test
    void createRejectsAuthenticatedContextWithoutResolvedUserId() {
        authenticateAs(null, "simulated@savory.test");

        assertThrows(UnauthorizedException.class, () -> promocionService.create(validPercentageRequest(1)));
        verifyNoInteractions(restaurantRepository, restaurantStaffRepository, discountRepository);
    }

    private static void authenticateAs(Integer userId, String email) {
        UserPrincipal principal = new UserPrincipal(
                userId,
                email,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal,
                "token",
                principal.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private static PromocionRequest validPercentageRequest(int restaurantId) {
        PromocionRequest request = new PromocionRequest();
        request.setRestaurantId(restaurantId);
        request.setName("REST-05 Promo");
        request.setApplicableProducts("Menu del dia");
        request.setType(DiscountType.PERCENTAGE.name());
        request.setAmount(new BigDecimal("15.00"));
        request.setMaxUses(20);
        return request;
    }
}
