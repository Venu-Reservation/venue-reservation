package com.venue.common

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/** Settings under `app.*`. Secrets come from env vars outside the dev profile. */
@ConfigurationProperties("app")
data class AppProperties(
	val jwt: Jwt,
	val payments: Payments,
) {
	data class Jwt(
		/** HMAC key for signing tokens (HS256). At least 32 characters. */
		val secret: String,
		val ttl: Duration = Duration.ofDays(7),
	)

	data class Payments(
		/** Shared secret the payment provider signs webhook bodies with. */
		val webhookSecret: String,
	)
}
