package com.apg.promociones.apg_api.controller;

import com.apg.promociones.apg_api.dto.ErrorResponse;
import com.apg.promociones.apg_api.dto.PromocionRequest;
import com.apg.promociones.apg_api.dto.PromocionResponse;
import com.apg.promociones.apg_api.service.PromocionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/promociones")
@RequiredArgsConstructor
@Tag(name = "Promociones", description = "Promotion publishing endpoint")
public class PromocionController {

    private final PromocionService promocionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Publish a new promotion",
            description = "Creates a new promotion (discount) for a restaurant, validating format, conditions and limits."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Promotion created successfully"),
            @ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Restaurant not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public PromocionResponse create(@Valid @RequestBody PromocionRequest request) {
        return promocionService.create(request);
    }
}
