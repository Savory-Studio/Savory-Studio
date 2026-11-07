package com.apg.promociones.apg_api.service;

import com.apg.promociones.apg_api.dto.LoginDTOs.LoginRequest;
import com.apg.promociones.apg_api.dto.LoginDTOs.LoginResponse;
import com.apg.promociones.apg_api.dto.RegisterDTOs.RegisterRequest;
import com.apg.promociones.apg_api.dto.RegisterDTOs.RegisterResponse;
import com.apg.promociones.apg_api.exception.BusinessRuleException;
import com.apg.promociones.apg_api.exception.UnauthorizedException;
import com.apg.promociones.apg_api.model.User;
import com.apg.promociones.apg_api.repository.UserRepository;
import com.apg.promociones.apg_api.service.LoginServices.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
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

    public LoginResponse login(LoginRequest request) {
        // 1. Buscar el usuario por email
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials."));

        // 2. Verificar la contraseña con PasswordEncoder
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            // Regla RN-17: Mensaje genérico para no revelar si existe el correo
            throw new UnauthorizedException("Invalid credentials.");
        }

        // 3. Generar el JWT firmado
        String token = jwtService.generateToken(user);
        String expiresIn = String.valueOf(jwtService.getExpirationTimeInSeconds());

        // 4. Construir y retornar el DTO de respuesta
        return LoginResponse.of(user, token, expiresIn);
    }
}