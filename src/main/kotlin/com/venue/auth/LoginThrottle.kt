package com.venue.auth

import com.venue.common.TooManyRequestsException
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Slows down password guessing on one account, even from many IPs. After
 * [FREE_FAILURES] wrong passwords in a row, each further login for that email
 * must wait 1, 2, 4… seconds, up to [MAX_DELAY]. Growing delays instead of a
 * lockout, so nobody can lock a real user out by typing wrong passwords.
 * Unknown emails are tracked the same way, so the delay doesn't reveal which
 * accounts exist. Counts live in memory, per server.
 */
@Component
class LoginThrottle(private val clock: Clock) {
	private val failures = ConcurrentHashMap<String, Failures>()

	/** Throws TOO_MANY_REQUESTS if [email] must still wait after earlier failures. */
	fun checkAllowed(email: String) {
		val entry = failures[email] ?: return
		val now = Instant.now(clock)
		if (now.isBefore(entry.nextAttemptAt)) throw TooManyRequestsException(Duration.between(now, entry.nextAttemptAt))
	}

	fun recordFailure(email: String) {
		val now = Instant.now(clock)
		failures.compute(email) { _, current ->
			val count = if (current == null || current.isStale(now)) 1 else current.count + 1
			Failures(count = count, lastFailureAt = now, nextAttemptAt = now + delayAfter(count))
		}
	}

	fun recordSuccess(email: String) {
		failures.remove(email)
	}

	/** Drops accounts with no failure in [RESET_AFTER]. */
	@Scheduled(fixedDelayString = "PT5M")
	fun forgetOldFailures() {
		val now = Instant.now(clock)
		failures.values.removeIf { it.isStale(now) }
	}

	/** Forgets every count. Tests call this so each test starts fresh. */
	fun clear() = failures.clear()

	private fun delayAfter(count: Int): Duration {
		if (count < FREE_FAILURES) return Duration.ZERO
		// 1 s after the 5th failure, then doubling. The shift is capped so it can't overflow.
		val seconds = 1L shl minOf(count - FREE_FAILURES, 20)
		return minOf(Duration.ofSeconds(seconds), MAX_DELAY)
	}

	private data class Failures(val count: Int, val lastFailureAt: Instant, val nextAttemptAt: Instant) {
		fun isStale(now: Instant) = !now.isBefore(lastFailureAt + RESET_AFTER)
	}

	companion object {
		const val FREE_FAILURES = 5
		val MAX_DELAY: Duration = Duration.ofMinutes(5)
		val RESET_AFTER: Duration = Duration.ofMinutes(15)
	}
}
