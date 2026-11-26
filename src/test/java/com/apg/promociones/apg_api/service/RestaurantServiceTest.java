package com.apg.promociones.apg_api.service;

import com.apg.promociones.apg_api.dto.CreateRestaurantRequest;
import com.apg.promociones.apg_api.dto.CreateRestaurantResponse;
import com.apg.promociones.apg_api.dto.RestaurantDTOs.RestaurantDetailResponse;
import com.apg.promociones.apg_api.dto.UpdateRestaurantRequest;
import com.apg.promociones.apg_api.dto.UpdateRestaurantResponse;
import com.apg.promociones.apg_api.exception.BadRequestException;
import com.apg.promociones.apg_api.exception.ResourceNotFoundException;
import com.apg.promociones.apg_api.exception.RestaurantStaffExceptions.PermisoDenegadoException;
import com.apg.promociones.apg_api.model.Restaurant;
import com.apg.promociones.apg_api.model.User;
import com.apg.promociones.apg_api.repository.RestaurantRepository;
import com.apg.promociones.apg_api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestaurantServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RestaurantRepository restaurantRepository;

    @InjectMocks
    private RestaurantService restaurantService;

    private User owner;
    private CreateRestaurantRequest request;

    @BeforeEach
    void setUp() {
        owner = User.builder().id(5).firstName("Ana").lastName("Perez").build();
        request = new CreateRestaurantRequest("La Cazuela", "Comida típica", "Calle 45 #12-30");
    }

    @Test
    void createsRestaurantWithAuthenticatedOwner() {
        when(userRepository.findById(5)).thenReturn(Optional.of(owner));
        when(restaurantRepository.save(any(Restaurant.class))).thenAnswer(invocation -> {
            Restaurant r = invocation.getArgument(0);
            r.setId(10);
            return r;
        });

        CreateRestaurantResponse response = restaurantService.create(5, request);

        assertEquals(10, response.getRestaurantId());
        assertEquals("La Cazuela", response.getName());
        assertEquals(5, response.getOwnerId());
        assertEquals("Comida típica", response.getDescription());
        assertEquals("Calle 45 #12-30", response.getFullAddress());
        assertNotNull(response.getCreationDate());
        assertEquals("Restaurante registrado exitosamente.", response.getMessage());

        ArgumentCaptor<Restaurant> captor = ArgumentCaptor.forClass(Restaurant.class);
        verify(restaurantRepository).save(captor.capture());
        assertEquals(owner, captor.getValue().getOwner());
        assertEquals("La Cazuela", captor.getValue().getName());
    }

    @Test
    void createsRestaurantWithNullDescription() {
        request.setDescription(null);
        when(userRepository.findById(5)).thenReturn(Optional.of(owner));
        when(restaurantRepository.save(any(Restaurant.class))).thenAnswer(invocation -> {
            Restaurant r = invocation.getArgument(0);
            r.setId(11);
            return r;
        });

        CreateRestaurantResponse response = restaurantService.create(5, request);

        assertEquals(11, response.getRestaurantId());
        assertEquals(null, response.getDescription());
    }

    @Test
    void throws404WhenOwnerDoesNotExist() {
        when(userRepository.findById(999)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> restaurantService.create(999, request));

        assertEquals("User not found.", ex.getMessage());
        verify(restaurantRepository, never()).save(any());
    }

    @Test
    void updatesAllEditableFieldsWhenRequestedByOwner() {
        LocalDateTime created = LocalDateTime.of(2026, 11, 13, 10, 0);
        Restaurant existing = Restaurant.builder()
                .id(10)
                .name("La Cazuela")
                .owner(owner)
                .description("Comida típica")
                .fullAddress("Calle 45 #12-30")
                .creationDate(created)
                .build();

        UpdateRestaurantRequest updateRequest = new UpdateRestaurantRequest();
        updateRequest.setName("  La Cazuela Gourmet  ");
        updateRequest.setDescription("  Cocina de autor  ");
        updateRequest.setFullAddress("  Carrera 27 #34-10  ");

        when(restaurantRepository.findById(10)).thenReturn(Optional.of(existing));
        when(restaurantRepository.saveAndFlush(any(Restaurant.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateRestaurantResponse response = restaurantService.update(10, 5, updateRequest);

        assertEquals(10, response.getRestaurantId());
        assertEquals("La Cazuela Gourmet", response.getName());
        assertEquals(5, response.getOwnerId());
        assertEquals("Cocina de autor", response.getDescription());
        assertEquals("Carrera 27 #34-10", response.getFullAddress());
        assertEquals(created, response.getCreationDate());
        assertEquals("Restaurante actualizado exitosamente.", response.getMessage());
        assertEquals(owner, existing.getOwner());
        verify(restaurantRepository).saveAndFlush(existing);
    }

    @Test
    void supportsPartialUpdateAndPreservesUntouchedFields() {
        Restaurant existing = Restaurant.builder()
                .id(10)
                .name("La Cazuela")
                .owner(owner)
                .description("Comida típica")
                .fullAddress("Calle 45 #12-30")
                .build();

        UpdateRestaurantRequest updateRequest = new UpdateRestaurantRequest();
        updateRequest.setName("La Cazuela Express");

        when(restaurantRepository.findById(10)).thenReturn(Optional.of(existing));
        when(restaurantRepository.saveAndFlush(any(Restaurant.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateRestaurantResponse response = restaurantService.update(10, 5, updateRequest);

        assertEquals("La Cazuela Express", response.getName());
        assertEquals("Comida típica", response.getDescription());
        assertEquals("Calle 45 #12-30", response.getFullAddress());
        assertEquals(5, response.getOwnerId());
    }

    @Test
    void clearsDescriptionWhenProvidedAsNullOrBlank() {
        Restaurant existing = Restaurant.builder()
                .id(10)
                .name("La Cazuela")
                .owner(owner)
                .description("Comida típica")
                .fullAddress("Calle 45 #12-30")
                .build();

        UpdateRestaurantRequest updateRequest = new UpdateRestaurantRequest();
        updateRequest.setDescription("   ");

        when(restaurantRepository.findById(10)).thenReturn(Optional.of(existing));
        when(restaurantRepository.saveAndFlush(any(Restaurant.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateRestaurantResponse response = restaurantService.update(10, 5, updateRequest);

        assertNull(response.getDescription());
        assertEquals("La Cazuela", response.getName());
        assertEquals("Calle 45 #12-30", response.getFullAddress());
    }

    @Test
    void throwsBadRequestForInvalidIdsDisallowedOrInvalidFields() {
        UpdateRestaurantRequest validPartial = new UpdateRestaurantRequest();
        validPartial.setName("Nombre");

        assertThrows(BadRequestException.class, () -> restaurantService.update(null, 5, validPartial));
        assertThrows(BadRequestException.class, () -> restaurantService.update(0, 5, validPartial));
        assertThrows(BadRequestException.class, () -> restaurantService.update(-1, 5, validPartial));
        assertThrows(BadRequestException.class, () -> restaurantService.update(10, 5, null));
        assertThrows(BadRequestException.class, () -> restaurantService.update(10, 5, new UpdateRestaurantRequest()));

        UpdateRestaurantRequest withOwnerId = new UpdateRestaurantRequest();
        withOwnerId.setName("Nombre");
        withOwnerId.captureUnknownField("ownerId", 99);
        assertThrows(BadRequestException.class, () -> restaurantService.update(10, 5, withOwnerId));

        UpdateRestaurantRequest withRestaurantOwnerId = new UpdateRestaurantRequest();
        withRestaurantOwnerId.captureUnknownField("restaurantOwnerId", 99);
        assertThrows(BadRequestException.class, () -> restaurantService.update(10, 5, withRestaurantOwnerId));

        UpdateRestaurantRequest withCreationDate = new UpdateRestaurantRequest();
        withCreationDate.captureUnknownField("creationDate", "2026-01-01T00:00:00");
        assertThrows(BadRequestException.class, () -> restaurantService.update(10, 5, withCreationDate));

        UpdateRestaurantRequest blankName = new UpdateRestaurantRequest();
        blankName.setName("   ");
        assertThrows(BadRequestException.class, () -> restaurantService.update(10, 5, blankName));

        UpdateRestaurantRequest nullName = new UpdateRestaurantRequest();
        nullName.setName(null);
        assertThrows(BadRequestException.class, () -> restaurantService.update(10, 5, nullName));

        UpdateRestaurantRequest longName = new UpdateRestaurantRequest();
        longName.setName("A".repeat(256));
        assertThrows(BadRequestException.class, () -> restaurantService.update(10, 5, longName));

        UpdateRestaurantRequest blankAddress = new UpdateRestaurantRequest();
        blankAddress.setFullAddress(" ");
        assertThrows(BadRequestException.class, () -> restaurantService.update(10, 5, blankAddress));

        UpdateRestaurantRequest nullAddress = new UpdateRestaurantRequest();
        nullAddress.setFullAddress(null);
        assertThrows(BadRequestException.class, () -> restaurantService.update(10, 5, nullAddress));

        UpdateRestaurantRequest longAddress = new UpdateRestaurantRequest();
        longAddress.setFullAddress("B".repeat(256));
        assertThrows(BadRequestException.class, () -> restaurantService.update(10, 5, longAddress));

        verifyNoInteractions(restaurantRepository);
    }

    @Test
    void throwsNotFoundWhenUpdatingMissingRestaurant() {
        UpdateRestaurantRequest updateRequest = new UpdateRestaurantRequest();
        updateRequest.setName("Nuevo");
        when(restaurantRepository.findById(999)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> restaurantService.update(999, 5, updateRequest));

        assertEquals("Restaurant not found.", ex.getMessage());
        verify(restaurantRepository, never()).saveAndFlush(any());
    }

    @Test
    void throwsAccessDeniedWhenAuthenticatedUserDoesNotOwnRestaurant() {
        Restaurant existing = Restaurant.builder()
                .id(10)
                .name("La Cazuela")
                .owner(owner)
                .fullAddress("Calle 45 #12-30")
                .build();

        UpdateRestaurantRequest updateRequest = new UpdateRestaurantRequest();
        updateRequest.setName("Intento ajeno");
        when(restaurantRepository.findById(10)).thenReturn(Optional.of(existing));

        assertThrows(AccessDeniedException.class, () -> restaurantService.update(10, 99, updateRequest));
        verify(restaurantRepository, never()).saveAndFlush(any());
        assertEquals("La Cazuela", existing.getName());
    }

    @Test
    void returnsOwnedRestaurantsOrderedNewestFirst() {
        LocalDateTime olderTime = LocalDateTime.of(2026, 11, 10, 9, 0);
        LocalDateTime newerTime = LocalDateTime.of(2026, 11, 12, 15, 0);
        Restaurant newer = Restaurant.builder()
                .id(12)
                .name("Nuevo Bistro")
                .owner(owner)
                .description("Reciente")
                .fullAddress("Calle 2")
                .creationDate(newerTime)
                .build();
        Restaurant older = Restaurant.builder()
                .id(10)
                .name("Viejo Bistro")
                .owner(owner)
                .fullAddress("Calle 1")
                .creationDate(olderTime)
                .build();

        when(restaurantRepository.findByOwnerIdOrderByCreationDateDesc(5)).thenReturn(List.of(newer, older));

        List<RestaurantDetailResponse> result = restaurantService.getMyRestaurants(5);

        assertEquals(2, result.size());
        assertEquals(12, result.get(0).getRestaurantId());
        assertEquals("Nuevo Bistro", result.get(0).getName());
        assertEquals(10, result.get(1).getRestaurantId());
    }

    @Test
    void returnsRestaurantDetailWhenRequestedByOwner() {
        LocalDateTime created = LocalDateTime.of(2026, 11, 13, 10, 0);
        Restaurant existing = Restaurant.builder()
                .id(10)
                .name("La Cazuela")
                .owner(owner)
                .description("Comida típica")
                .fullAddress("Calle 45 #12-30")
                .creationDate(created)
                .build();
        when(restaurantRepository.findById(10)).thenReturn(Optional.of(existing));

        RestaurantDetailResponse detail = restaurantService.getById(10, 5);

        assertEquals(10, detail.getRestaurantId());
        assertEquals("La Cazuela", detail.getName());
        assertEquals(5, detail.getOwnerId());
        assertEquals("Comida típica", detail.getDescription());
        assertEquals("Calle 45 #12-30", detail.getFullAddress());
        assertEquals(created, detail.getCreationDate());
    }

    @Test
    void throwsBadRequestWhenGetByIdReceivesNonPositiveId() {
        assertThrows(BadRequestException.class, () -> restaurantService.getById(0, 5));
        assertThrows(BadRequestException.class, () -> restaurantService.getById(-3, 5));
        verifyNoInteractions(restaurantRepository);
    }

    @Test
    void throwsPermisoDenegadoWhenGetByIdRequestedByNonOwner() {
        Restaurant existing = Restaurant.builder()
                .id(10)
                .name("La Cazuela")
                .owner(owner)
                .fullAddress("Calle 45 #12-30")
                .build();
        when(restaurantRepository.findById(10)).thenReturn(Optional.of(existing));

        assertThrows(PermisoDenegadoException.class, () -> restaurantService.getById(10, 99));
    }

    @Test
    void throwsResourceNotFoundWhenGetByIdMissingRestaurant() {
        when(restaurantRepository.findById(999)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> restaurantService.getById(999, 5));
    }
}
