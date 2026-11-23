package com.apg.promociones.apg_api.service.PasswordRecovery;

import com.apg.promociones.apg_api.dto.PasswordRecoveryDTOs.ForgotPasswordRequest;
import com.apg.promociones.apg_api.dto.PasswordRecoveryDTOs.PasswordRecoveryResponse;
import com.apg.promociones.apg_api.dto.PasswordRecoveryDTOs.ResetPasswordRequest;
import com.apg.promociones.apg_api.exception.BadRequestException;
import com.apg.promociones.apg_api.model.PasswordResetToken;
import com.apg.promociones.apg_api.model.User;
import com.apg.promociones.apg_api.repository.PasswordResetTokenRepository;
import com.apg.promociones.apg_api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PasswordRecoveryService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String GENERIC_FORGOT_MESSAGE =
            "Si el correo está registrado, recibirás instrucciones para restablecer tu contraseña.";

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${app.password-reset.token-expiration-minutes:15}")
    private long expirationMinutes;

    @Transactional
    public PasswordRecoveryResponse requestPasswordReset(ForgotPasswordRequest request) {
        Optional<User> userOpt = userRepository.findByEmail(request.getEmail().trim());

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            LocalDateTime now = LocalDateTime.now();

            // Invalida tokens previos activos del mismo usuario
            tokenRepository.invalidateActiveTokensByUserId(user.getId(), now);

            String rawToken = generateSecureToken();
            String tokenHash = sha256Hex(rawToken);

            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .user(user)
                    .tokenHash(tokenHash)
                    .createdAt(now)
                    .expiresAt(now.plusMinutes(expirationMinutes))
                    .build();

            tokenRepository.save(resetToken);
            emailService.sendPasswordResetEmail(user.getEmail(), user.getFirstName(), rawToken);
        }

        // Siempre responde 200 con mensaje genérico (no filtra si el correo existe)
        return PasswordRecoveryResponse.builder()
                .message(GENERIC_FORGOT_MESSAGE)
                .build();
    }

    @Transactional
    public PasswordRecoveryResponse resetPassword(ResetPasswordRequest request) {
        ensureWithinBcryptByteLimit(request.getNewPassword());

        String tokenHash = sha256Hex(request.getToken().trim());
        PasswordResetToken resetToken = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BadRequestException("Invalid or expired password reset token."));

        if (resetToken.isUsed() || resetToken.isExpired()) {
            throw new BadRequestException("Invalid or expired password reset token.");
        }

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.saveAndFlush(user);

        resetToken.setUsedAt(LocalDateTime.now());
        tokenRepository.saveAndFlush(resetToken);

        return PasswordRecoveryResponse.builder()
                .message("Password has been reset successfully.")
                .build();
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private static void ensureWithinBcryptByteLimit(String rawPassword) {
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BadRequestException("Password exceeds the 72-byte BCrypt limit.");
        }
    }
}