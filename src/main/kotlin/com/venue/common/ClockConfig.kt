package com.venue.common

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/**
 * The time source. Code that asks this Clock for the time (`Instant.now(clock)`)
 * instead of `Instant.now()` can be tested at any moment, e.g. 8 days later.
 */
@Configuration
class ClockConfig {
	@Bean
	fun clock(): Clock = Clock.systemUTC()
}
