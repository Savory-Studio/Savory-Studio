package com.apg.promociones.apg_api.service.PasswordRecovery;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.password-reset.from-email}")
    private String fromEmail;

    @Value("${app.password-reset.frontend-url}")
    private String frontendResetUrl;

    @Value("${app.password-reset.token-expiration-minutes:15}")
    private long expirationMinutes;

    public void sendPasswordResetEmail(String toEmail, String firstName, String rawToken) {
        String resetLink = frontendResetUrl + "?token=" + rawToken;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("Savory Studio - Recuperación de contraseña");
        message.setText(
                "Hola " + firstName + ",\n\n"
                        + "Recibimos una solicitud para restablecer tu contraseña.\n"
                        + "Usa el siguiente enlace o token (válido por " + expirationMinutes + " minutos y de un solo uso):\n\n"
                        + "Enlace: " + resetLink + "\n"
                        + "Token: " + rawToken + "\n\n"
                        + "Si no solicitaste este cambio, puedes ignorar este correo."
        );

        mailSender.send(message);
    }
}
