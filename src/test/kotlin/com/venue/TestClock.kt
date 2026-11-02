package com.venue

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The real time plus an offset a test can move forward, e.g. `clock.advance(Duration.ofDays(8))`.
 * IntegrationTest resets it before each test. Only code that reads the injected Clock
 * sees the offset; Postgres's own now() never does.
 */
class TestClock : Clock() {
	@Volatile
	private var offset: Duration = Duration.ZERO

	fun advance(by: Duration) {
		offset += by
	}

	fun reset() {
		offset = Duration.ZERO
	}

	override fun instant(): Instant = Instant.now().plus(offset)

	override fun getZone(): ZoneId = ZoneOffset.UTC

	override fun withZone(zone: ZoneId): Clock = throw UnsupportedOperationException("TestClock is always UTC")
}

/** Replaces the app's Clock bean in tests. */
@TestConfiguration(proxyBeanMethods = false)
class TestClockConfiguration {
	@Bean
	@Primary
	fun testClock(): TestClock = TestClock()
}
