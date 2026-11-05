package com.apg.promociones.apg_api.dto.RegisterDTOs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterResponse {
    private Integer userId;
    private String firstName;
    private String lastName;
    private String email;
    private LocalDateTime createdAt;
    private String message;
}