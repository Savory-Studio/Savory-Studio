package com.apg.promociones.apg_api.service;

import com.apg.promociones.apg_api.dto.UserDTOs.UserProfileResponse;
import com.apg.promociones.apg_api.exception.UnauthorizedException;
import com.apg.promociones.apg_api.exception.UserExceptions.CampoNoEditableException;
import com.apg.promociones.apg_api.exception.UserExceptions.CorreoYaRegistradoException;
import com.apg.promociones.apg_api.exception.UserExceptions.PerfilActualizacionInvalidaException;
import com.apg.promociones.apg_api.exception.UserExceptions.UsuarioNoEncontradoException;
import com.apg.promociones.apg_api.model.User;
import com.apg.promociones.apg_api.repository.UserRepository;
import com.apg.promociones.apg_api.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "firstName", "secondName", "lastName", "birthday", "email"
    );

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserProfileResponse getAuthenticatedUserProfile() {
        Integer currentUserId = getAuthenticatedUserId();

        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new UsuarioNoEncontradoException("Usuario autenticado no encontrado."));

        return toProfileResponse(user);
    }

    @Transactional
    public UserProfileResponse updateAuthenticatedUserProfile(Map<String, Object> payload) {
        Integer currentUserId = getAuthenticatedUserId();

        if (payload == null || payload.isEmpty()) {
            throw new PerfilActualizacionInvalidaException(
                    "Debe proporcionar al menos un campo editable para actualizar (firstName, secondName, lastName, birthday o email).");
        }

        // Detectar campos prohibidos o no editables
        List<String> forbiddenFields = payload.keySet().stream()
                .filter(key -> !ALLOWED_FIELDS.contains(key))
                .toList();

        if (!forbiddenFields.isEmpty()) {
            throw new CampoNoEditableException(
                    "Los siguientes campos no están permitidos o no son editables: "
                            + String.join(", ", forbiddenFields) + ".");
        }

        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new UsuarioNoEncontradoException("Usuario autenticado no encontrado."));

        // Validar y actualizar cada campo permitido enviado
        if (payload.containsKey("firstName")) {
            String firstName = extractString(payload.get("firstName"), "firstName");
            if (firstName == null || firstName.trim().isEmpty()) {
                throw new PerfilActualizacionInvalidaException("El primer nombre no puede estar vacío.");
            }
            if (firstName.trim().length() > 100) {
                throw new PerfilActualizacionInvalidaException("El primer nombre no puede superar los 100 caracteres.");
            }
            user.setFirstName(firstName.trim());
        }

        if (payload.containsKey("secondName")) {
            String secondName = extractString(payload.get("secondName"), "secondName");
            if (secondName == null || secondName.trim().isEmpty()) {
                user.setSecondName(null);
            } else {
                if (secondName.trim().length() > 100) {
                    throw new PerfilActualizacionInvalidaException("El segundo nombre no puede superar los 100 caracteres.");
                }
                user.setSecondName(secondName.trim());
            }
        }

        if (payload.containsKey("lastName")) {
            String lastName = extractString(payload.get("lastName"), "lastName");
            if (lastName == null || lastName.trim().isEmpty()) {
                throw new PerfilActualizacionInvalidaException("El apellido no puede estar vacío.");
            }
            if (lastName.trim().length() > 100) {
                throw new PerfilActualizacionInvalidaException("El apellido no puede superar los 100 caracteres.");
            }
            user.setLastName(lastName.trim());
        }

        if (payload.containsKey("birthday")) {
            String birthdayStr = extractString(payload.get("birthday"), "birthday");
            if (birthdayStr == null || birthdayStr.trim().isEmpty()) {
                throw new PerfilActualizacionInvalidaException("La fecha de nacimiento no puede estar vacía.");
            }
            try {
                LocalDate birthday = LocalDate.parse(birthdayStr.trim());
                if (!birthday.isBefore(LocalDate.now())) {
                    throw new PerfilActualizacionInvalidaException("La fecha de nacimiento debe ser una fecha pasada.");
                }
                user.setBirthday(birthday);
            } catch (DateTimeParseException ex) {
                throw new PerfilActualizacionInvalidaException("El formato de fecha de nacimiento debe ser YYYY-MM-DD.");
            }
        }

        if (payload.containsKey("email")) {
            String email = extractString(payload.get("email"), "email");
            if (email == null || email.trim().isEmpty()) {
                throw new PerfilActualizacionInvalidaException("El correo electrónico no puede estar vacío.");
            }
            String normalizedEmail = email.trim().toLowerCase();
            if (normalizedEmail.length() > 150 || !EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
                throw new PerfilActualizacionInvalidaException("El formato del correo electrónico no es válido.");
            }
            if (!normalizedEmail.equalsIgnoreCase(user.getEmail())
                    && userRepository.existsByEmail(normalizedEmail)) {
                throw new CorreoYaRegistradoException("El correo electrónico ya está registrado por otro usuario.");
            }
            user.setEmail(normalizedEmail);
        }

        User updatedUser = userRepository.save(user);
        return toProfileResponse(updatedUser);
    }

    private String extractString(Object value, String fieldName) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof String str)) {
            throw new PerfilActualizacionInvalidaException("El campo '" + fieldName + "' debe ser una cadena de texto.");
        }
        return str;
    }

    private Integer getAuthenticatedUserId() {
        return SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("No se pudo identificar al usuario autenticado."));
    }

    private UserProfileResponse toProfileResponse(User user) {
        return UserProfileResponse.builder()
                .userId(user.getId())
                .firstName(user.getFirstName())
                .secondName(user.getSecondName())
                .lastName(user.getLastName())
                .birthday(user.getBirthday())
                .joinDate(user.getJoinDate())
                .email(user.getEmail())
                .build();
    }
}