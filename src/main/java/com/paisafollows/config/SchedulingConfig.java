package com.paisafollows.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on @Scheduled: methods that Spring runs automatically every N milliseconds. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}