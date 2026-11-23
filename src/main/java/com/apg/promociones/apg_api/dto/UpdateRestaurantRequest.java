package com.apg.promociones.apg_api.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Request payload for partially updating an existing restaurant's editable fields.
 * Tracks which fields were explicitly provided and captures any disallowed properties
 * (such as ownership fields) so the service can reject them with HTTP 400.
 */
@Getter
@NoArgsConstructor
@Schema(description = "Request payload to update editable restaurant fields (name, description, fullAddress). Ownership cannot be reassigned.")
public class UpdateRestaurantRequest {

    @Schema(description = "Updated restaurant name (non-blank, max 255 characters)", example = "La Cazuela Criolla Gourmet")
    private String name;

    @Schema(description = "Updated restaurant description (optional; null or blank clears the description)", example = "Cocina típica colombiana renovada")
    private String description;

    @Schema(description = "Updated full street address (non-blank, max 255 characters)", example = "Carrera 27 #34-10, Bucaramanga")
    private String fullAddress;

    @JsonIgnore
    @Schema(hidden = true)
    private boolean nameProvided;

    @JsonIgnore
    @Schema(hidden = true)
    private boolean descriptionProvided;

    @JsonIgnore
    @Schema(hidden = true)
    private boolean fullAddressProvided;

    @JsonIgnore
    @Schema(hidden = true)
    private final Set<String> unknownFields = new LinkedHashSet<>();

    public void setName(String name) {
        this.name = name;
        this.nameProvided = true;
    }

    public void setDescription(String description) {
        this.description = description;
        this.descriptionProvided = true;
    }

    public void setFullAddress(String fullAddress) {
        this.fullAddress = fullAddress;
        this.fullAddressProvided = true;
    }

    @JsonAnySetter
    public void captureUnknownField(String fieldName, Object value) {
        this.unknownFields.add(fieldName);
    }

    @JsonIgnore
    public Set<String> getUnknownFields() {
        return Collections.unmodifiableSet(unknownFields);
    }

    @JsonIgnore
    public boolean hasAnyEditableField() {
        return nameProvided || descriptionProvided || fullAddressProvided;
    }
}
