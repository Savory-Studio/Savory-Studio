package com.apg.promociones.apg_api.util;

import java.util.UUID;

public final class CouponHashGenerator {

    private CouponHashGenerator() {}

    public static String generate() {
        return UUID.randomUUID().toString();
    }
}
