package com.apg.promociones.apg_api.model.enums;

/**
 * Maps to MySQL ENUM('Active','Suspended','Finished') in discounts table.
 * Values MUST match the DB strings exactly (case-sensitive).
 */
public enum DiscountStatus {
    Active,
    Suspended,
    Finished
}
