package com.venue.auth

import com.venue.common.TooManyRequestsException
import com.venue.common.writeError
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Allows each client IP [MAX_REQUESTS] signup and login requests per minute.
 * It runs before the controller, so a blocked request never reaches password
 * hashing. Counts live in memory, so with several servers each counts alone.
 *
 * Behind a reverse proxy, remoteAddr is the proxy's address. Set
 * server.forward-headers-strategy=native there so it's the client's.
 */
@Component
class AuthRateLimitFilter(private val clock: Clock) : OncePerRequestFilter() {
	private val windows = ConcurrentHashMap<String, Window>()

	override fun shouldNotFilter(request: HttpServletRequest): Boolean =
		request.method != "POST" || !request.requestURI.startsWith("/api/v1/auth/")

	override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
		val now = Instant.now(clock)
		// compute() updates one IP's entry atomically, even with parallel requests.
		val window = windows.compute(request.remoteAddr) { _, current ->
			if (current == null || current.isOver(now)) Window(start = now, count = 1) else current.copy(count = current.count + 1)
		}!!
		if (window.count > MAX_REQUESTS) {
			val error = TooManyRequestsException(Duration.between(now, window.start + WINDOW))
			response.setHeader(HttpHeaders.RETRY_AFTER, error.retryAfterSeconds.toString())
			response.writeError(error.status.value(), error.code, error.message ?: "")
			return
		}
		chain.doFilter(request, response)
	}

	/** Drops finished windows, so the map doesn't keep every IP ever seen. */
	@Scheduled(fixedDelayString = "PT5M")
	fun forgetFinishedWindows() {
		val now = Instant.now(clock)
		windows.values.removeIf { it.isOver(now) }
	}

	/** Forgets every count. Tests call this so each test starts fresh. */
	fun clear() = windows.clear()

	private data class Window(val start: Instant, val count: Int) {
		fun isOver(now: Instant) = !now.isBefore(start + WINDOW)
	}

	companion object {
		const val MAX_REQUESTS = 10
		val WINDOW: Duration = Duration.ofMinutes(1)
	}
}
