package com.apg.promociones.apg_api.model.enums;

/**
 * Maps to MySQL ENUM('PERCENTAGE','FIXED_VALUE','IXJ') in discounts table.
 * IXJ = "pague X lleve Y" (buy X get Y).
 */
public enum DiscountType {
    PERCENTAGE,
    FIXED_VALUE,
    IXJ
}
