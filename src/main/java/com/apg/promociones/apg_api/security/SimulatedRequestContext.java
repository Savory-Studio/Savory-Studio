package com.apg.promociones.apg_api.security;

import com.apg.promociones.apg_api.exception.UnauthorizedException;

public final class SimulatedRequestContext {

    private static final ThreadLocal<SimulatedCaller> HOLDER = new ThreadLocal<>();

    private SimulatedRequestContext() {
    }

    public static void set(SimulatedCaller caller) {
        HOLDER.set(caller);
    }

    public static SimulatedCaller get() {
        return HOLDER.get();
    }

    public static Integer requireUserId() {
        SimulatedCaller caller = HOLDER.get();
        if (caller == null || caller.userId() == null) {
            throw new UnauthorizedException(
                    "The client could not be identified. Please send the X-User-Id header.");
        }
        return caller.userId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
