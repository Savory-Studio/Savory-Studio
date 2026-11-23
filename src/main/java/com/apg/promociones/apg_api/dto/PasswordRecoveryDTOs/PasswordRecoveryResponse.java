package com.apg.promociones.apg_api.dto.PasswordRecoveryDTOs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordRecoveryResponse {
    private String message;
}