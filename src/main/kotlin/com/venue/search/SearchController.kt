package com.venue.search

import com.venue.common.PageResponse
import com.venue.venues.VenueResponse
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.LocalDate

/** Public browsing: no token needed. */
@RestController
@RequestMapping("/api/v1/venues")
class SearchController(private val search: SearchService) {

	/**
	 * Venues in a city. With startTime and endTime (UTC ISO-8601, half-open), only
	 * venues free for that whole range. page is zero-based.
	 */
	@GetMapping("/search")
	fun search(
		@RequestParam city: String,
		@RequestParam(required = false) startTime: Instant?,
		@RequestParam(required = false) endTime: Instant?,
		@RequestParam(defaultValue = "0") page: Int,
		@RequestParam(defaultValue = "20") size: Int,
	): PageResponse<VenueResponse> = search.search(city, startTime, endTime, page, size)

	/** Hourly slots for one day (YYYY-MM-DD, in the venue's time zone). */
	@GetMapping("/{id}/availability")
	fun availability(
		@PathVariable id: Long,
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
	): Availability = search.availability(id, date)
}
