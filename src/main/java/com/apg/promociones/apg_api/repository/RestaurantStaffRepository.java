package com.apg.promociones.apg_api.repository;

import com.apg.promociones.apg_api.model.RestaurantStaff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RestaurantStaffRepository extends JpaRepository<RestaurantStaff, Integer> {

    boolean existsByUserIdAndRestaurantId(Integer userId, Integer restaurantId);

    List<RestaurantStaff> findByRestaurantId(Integer restaurantId);

    List<RestaurantStaff> findByUserId(Integer userId);
    Optional<RestaurantStaff> findByUserIdAndRestaurantId(Integer userId, Integer restaurantId);
}
