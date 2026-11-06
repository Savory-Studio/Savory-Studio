package com.apg.promociones.apg_api.dto.RegisterDTOs;

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
public class RegisterResponse {
    private Integer id;
    private String firstName;
    private String secondName;
    private String lastName;
    private LocalDate birthday;
    private String email;
    private LocalDateTime joinDate;
}
