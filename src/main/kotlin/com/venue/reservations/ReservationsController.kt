package com.venue.reservations

import com.venue.common.ValidationException
import com.venue.common.security.userId
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/** All reservation endpoints need a token; "mine" always means the token's user. */
@RestController
@RequestMapping("/api/v1/reservations")
class ReservationsController(private val reservations: ReservationService) {

	/** Books a venue as a PENDING hold that must be paid within 10 minutes (see expiresAt). */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	fun create(@AuthenticationPrincipal jwt: Jwt, @RequestBody request: ReservationRequest): ReservationResponse =
		reservations.reserve(jwt.userId(), request.venueId, request.startTime, request.endTime)

	/**
	 * Your bookings, or with as=host the bookings on venues you host. Turning the
	 * query strings into enums is HTTP work, so it stays here; the rules are in the service.
	 * when=upcoming or when=past narrows the list; without it, everything, newest first.
	 */
	@GetMapping
	fun list(
		@AuthenticationPrincipal jwt: Jwt,
		@RequestParam("when", required = false) periodParam: String?,
		@RequestParam("as", required = false) roleParam: String?,
	): List<ReservationResponse> {
		val period = when (periodParam?.lowercase()) {
			null -> null
			"upcoming" -> ListPeriod.UPCOMING
			"past" -> ListPeriod.PAST
			else -> throw ValidationException("when must be upcoming or past.")
		}
		return when (roleParam?.lowercase()) {
			null, "guest" -> reservations.listForRenter(jwt.userId(), period)
			"host" -> reservations.listForHost(jwt.userId(), period)
			else -> throw ValidationException("as must be guest or host.")
		}
	}

	@GetMapping("/{id}")
	fun get(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: Long): ReservationResponse =
		reservations.get(jwt.userId(), id)

	/** Cancels and returns the updated reservation. */
	@DeleteMapping("/{id}")
	fun cancel(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: Long): ReservationResponse =
		reservations.cancel(jwt.userId(), id)
}

/** Times are UTC ISO-8601; the range is half-open [startTime, endTime). */
data class ReservationRequest(val venueId: Long?, val startTime: Instant?, val endTime: Instant?)
