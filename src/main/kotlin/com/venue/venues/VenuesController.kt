package com.venue.venues

import com.venue.common.security.userId
import com.venue.common.validate
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.ZoneId

@RestController
@RequestMapping("/api/v1/venues")
class VenuesController(private val venues: VenueService) {

	/** Any signed-in user can list a venue and becomes its host. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	fun create(@AuthenticationPrincipal jwt: Jwt, @RequestBody request: VenueRequest): VenueResponse =
		venues.create(jwt.userId(), request.toInput())

	/** The caller's own active listings, newest first. */
	@GetMapping
	fun listMine(@AuthenticationPrincipal jwt: Jwt): List<VenueResponse> = venues.listHostedBy(jwt.userId())

	@GetMapping("/{id}")
	fun get(@PathVariable id: Long): VenueResponse = venues.get(id)

	@PutMapping("/{id}")
	fun update(
		@AuthenticationPrincipal jwt: Jwt,
		@PathVariable id: Long,
		@RequestBody request: VenueRequest,
	): VenueResponse = venues.update(jwt.userId(), id, request.toInput())

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	fun delete(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: Long) = venues.delete(jwt.userId(), id)
}

/** Request body for POST and PUT. There's no hostId: the host is the caller. */
data class VenueRequest(
	val name: String?,
	val capacity: Int?,
	val sizeSqft: Int?,
	val city: String?,
	val state: String?,
	val timezone: String?,
	val hourlyRate: BigDecimal?,
	val imageUrls: List<String>?,
)

private const val MAX_IMAGES = 20
private val MAX_RATE = BigDecimal("99999999.99")
private val IMAGE_URL = Regex("""^https?://\S+$""")

/** Applies the shared validation rules (docs/project.md) and returns clean values. */
private fun VenueRequest.toInput(): VenueInput {
	val name = name?.trim().orEmpty()
	val city = city?.trim().orEmpty()
	val state = state?.trim().orEmpty()
	val timezone = timezone?.trim().orEmpty()
	val imageUrls = imageUrls.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }

	validate(name.isNotEmpty()) { "Name is required." }
	validate(name.length <= 255) { "Name must be 255 characters or fewer." }
	validate(capacity != null && capacity >= 1) { "Capacity must be a whole number of at least 1." }
	validate(sizeSqft == null || sizeSqft >= 1) { "Size must be a whole number of at least 1." }
	validate(city.isNotEmpty() && state.isNotEmpty() && timezone.isNotEmpty()) {
		"City, state and time zone are required."
	}
	validate(city.length <= 100) { "City must be 100 characters or fewer." }
	validate(state.length <= 50) { "State must be 50 characters or fewer." }
	// Region names only (America/New_York), not offsets like +05:00, so daylight saving works.
	validate(timezone in ZoneId.getAvailableZoneIds()) { "Time zone must be an IANA name like America/New_York." }
	val rate = hourlyRate
	validate(rate != null && rate > BigDecimal.ZERO && rate.stripTrailingZeros().scale() <= 2 && rate <= MAX_RATE) {
		"Hourly rate must be more than 0, with at most 2 decimal places."
	}
	validate(imageUrls.size <= MAX_IMAGES) { "A venue can have at most $MAX_IMAGES photos." }
	validate(imageUrls.all { it.length <= 500 && IMAGE_URL.matches(it) }) {
		"Photo links must be http(s) URLs of 500 characters or fewer."
	}

	return VenueInput(name, capacity!!, sizeSqft, city, state, timezone, rate!!.setScale(2), imageUrls)
}
