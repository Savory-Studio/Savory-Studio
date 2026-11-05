package com.apg.promociones.apg_api.repository;

import com.apg.promociones.apg_api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    boolean existsByEmail(String email); // o existsByUserEmail
    Optional<User> findByEmail(String email);
}
