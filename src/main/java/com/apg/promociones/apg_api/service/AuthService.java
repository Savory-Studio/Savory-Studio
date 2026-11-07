package com.apg.promociones.apg_api.service;

import com.apg.promociones.apg_api.dto.LoginDTOs.LoginRequest;
import com.apg.promociones.apg_api.dto.LoginDTOs.LoginResponse;
import com.apg.promociones.apg_api.dto.RegisterDTOs.RegisterRequest;
import com.apg.promociones.apg_api.dto.RegisterDTOs.RegisterResponse;
import com.apg.promociones.apg_api.exception.BadRequestException;
import com.apg.promociones.apg_api.exception.BusinessRuleException;
import com.apg.promociones.apg_api.exception.UnauthorizedException;
import com.apg.promociones.apg_api.model.User;
import com.apg.promociones.apg_api.repository.UserRepository;
import com.apg.promociones.apg_api.service.LoginServices.JwtService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
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
        if (request.getPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BadRequestException("Password exceeds the 72-byte BCrypt limit.");
        }
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        User newUser = User.builder()
                .firstName(request.getFirstName())
                .secondName(request.getSecondName())
                .lastName(request.getLastName())
                .birthday(request.getBirthday())
                .joinDate(LocalDateTime.now())
                .email(request.getEmail())
                .passwordHash(encodedPassword)
                .build();

        User savedUser;
        try {
            savedUser = userRepository.saveAndFlush(newUser);
        } catch (DataIntegrityViolationException ex) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new BusinessRuleException("Email address is already registered.");
            }
            throw ex;
        }

        return RegisterResponse.builder()
                .id(savedUser.getId())
                .firstName(savedUser.getFirstName())
                .secondName(savedUser.getSecondName())
                .birthday(savedUser.getBirthday())
                .lastName(savedUser.getLastName())
                .email(savedUser.getEmail())
                .joinDate(savedUser.getJoinDate())
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