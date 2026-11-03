package com.venue.venues

import com.venue.common.security.userId
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

@RestController
@RequestMapping("/api/v1/venues")
class VenuesController(private val venues: VenueService) {

	/** Any signed-in user can list a venue and becomes its host. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	fun create(@AuthenticationPrincipal jwt: Jwt, @RequestBody request: VenueRequest): VenueResponse =
		venues.create(jwt.userId(), request)

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
	): VenueResponse = venues.update(jwt.userId(), id, request)

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	fun delete(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: Long) = venues.delete(jwt.userId(), id)
}
