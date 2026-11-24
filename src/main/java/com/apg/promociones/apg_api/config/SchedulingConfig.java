package com.apg.promociones.apg_api.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables background scheduling for automatic promotion lifecycle closure.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
        name = "app.promotion-lifecycle.scheduling-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class SchedulingConfig {
}
