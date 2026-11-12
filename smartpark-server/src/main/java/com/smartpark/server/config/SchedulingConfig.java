package com.smartpark.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables {@code @Scheduled} background jobs (see the {@code scheduler} package). */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfig {
}
