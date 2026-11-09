package com.apg.promociones.apg_api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Standardized error response payload")
public class ErrorResponse {

    @Schema(description = "HTTP status code", example = "401")
    private int status;

    @Schema(description = "Descriptive error message", example = "Invalid credentials.")
    private String message;

    @Builder.Default
    @Schema(description = "Server timestamp when the error occurred", example = "2026-11-09T17:30:00")
    private LocalDateTime timestamp = LocalDateTime.now();

    public ErrorResponse(int status, String message) {
        this.status = status;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }
}
