package com.apg.promociones.apg_api.dto.UserDTOs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
// Expone únicamente los campos seguros del titular (excluyendo passwordHash)
public class UserProfileResponse {

    private Integer userId;
    private String firstName;
    private String secondName;
    private String lastName;
    private LocalDate birthday;
    private LocalDateTime joinDate;
    private String email;
}