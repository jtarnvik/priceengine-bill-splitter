package com.tarnvik.pbs.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * The time, as a bean, so that the receipt's expiry can be tested without waiting. The system's zone, Stockholm on
 * the Mini, gives the date in the Swish message.
 */
@Configuration
public class ClockConfig {
  @Bean
  public Clock clock() {
    return Clock.systemDefaultZone();
  }
}
