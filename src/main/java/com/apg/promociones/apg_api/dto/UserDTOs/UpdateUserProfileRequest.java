package com.apg.promociones.apg_api.dto.UserDTOs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserProfileRequest {
    private String firstName;
    private String secondName;
    private String lastName;
    private LocalDate birthday;
    private String email;
}