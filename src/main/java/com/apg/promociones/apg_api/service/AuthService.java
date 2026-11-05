package com.apg.promociones.apg_api.service;

import com.apg.promociones.apg_api.dto.RegisterDTOs.RegisterRequest;
import com.apg.promociones.apg_api.dto.RegisterDTOs.RegisterResponse;
import com.apg.promociones.apg_api.exception.BusinessRuleException;
import com.apg.promociones.apg_api.model.User;
import com.apg.promociones.apg_api.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public RegisterResponse register(RegisterRequest request) {
        // RN-16: Validar unicidad del correo
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessRuleException("Email address is already registered.");
        }

        // RN-15: Encriptar contraseña con BCrypt
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        User newUser = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .birthday(request.getBirthDate())
                .joinDate(LocalDateTime.now())
                .email(request.getEmail())
                .passwordHash(encodedPassword)
                .build();

        User savedUser = userRepository.save(newUser);

        return RegisterResponse.builder()
                .userId(savedUser.getId())
                .firstName(savedUser.getFirstName())
                .lastName(savedUser.getLastName())
                .email(savedUser.getEmail())
                .createdAt(savedUser.getJoinDate())
                .message("User registered successfully.")
                .build();
    }
}