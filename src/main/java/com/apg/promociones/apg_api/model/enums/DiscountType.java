package com.apg.promociones.apg_api.model.enums;

/**
 * Maps to PostgreSQL discount_type_enum in discounts table.
 * IXJ = "pague X lleve Y" (buy X get Y).
 */
public enum DiscountType {
    PERCENTAGE,
    FIXED_VALUE,
    IXJ
}
