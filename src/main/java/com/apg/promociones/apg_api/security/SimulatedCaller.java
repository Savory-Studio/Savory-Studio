package com.apg.promociones.apg_api.security;

/**
 * Identity extracted from simulated HTTP headers (Sprint 1, no login module).
 */
public record SimulatedCaller(Integer userId, Integer restaurantId) {
}
