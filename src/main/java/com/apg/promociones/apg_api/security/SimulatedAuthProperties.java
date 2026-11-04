package com.apg.promociones.apg_api.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.simulated-auth")
public class SimulatedAuthProperties {

    /**
     * When true, /api requests must send the simulated identity headers.
     */
    private boolean enabled = true;

    /**
     * Shared test token accepted as Bearer or as the password of Basic auth.
     * Replaced by JWT in the security sprint.
     */
    private String token = "test-token";
}
