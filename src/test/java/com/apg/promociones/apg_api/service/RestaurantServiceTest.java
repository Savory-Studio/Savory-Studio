package com.apg.promociones.apg_api.service;

import com.apg.promociones.apg_api.dto.CreateRestaurantRequest;
import com.apg.promociones.apg_api.dto.CreateRestaurantResponse;
import com.apg.promociones.apg_api.exception.ResourceNotFoundException;
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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
}
