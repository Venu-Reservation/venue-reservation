package com.venue.search

import com.venue.common.PageResponse
import com.venue.common.TimeRange
import com.venue.common.validate
import com.venue.reservations.FIRST_START_HOUR
import com.venue.reservations.LAST_START_HOUR
import com.venue.reservations.ReservationService
import com.venue.venues.VenueResponse
import com.venue.venues.VenueService
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Finds what's available. Combines the venues and reservations modules through
 * their service interfaces, without reading either module's tables.
 */
@Service
class SearchService(
	private val venues: VenueService,
	private val reservations: ReservationService,
	private val clock: Clock,
) {
	/**
	 * Venues in [city]. With [startTime] and [endTime] (send both or neither), only
	 * those free for that whole half-open range. [page] is zero-based.
	 */
	fun search(city: String, startTime: Instant?, endTime: Instant?, page: Int, size: Int): PageResponse<VenueResponse> {
		validate(city.isNotBlank()) { "City is required." }
		validate((startTime == null) == (endTime == null)) { "Send both startTime and endTime, or neither." }
		validate(startTime == null || startTime < endTime) { "startTime must be before endTime." }
		validate(page >= 0) { "page must be 0 or more." }
		validate(size in 1..MAX_PAGE_SIZE) { "size must be between 1 and $MAX_PAGE_SIZE." }

		val range = if (startTime != null && endTime != null) TimeRange(startTime, endTime) else null
		val booked = range?.let { reservations.venuesBookedDuring(it) } ?: emptySet()
		return venues.search(city.trim(), booked, page, size)
	}

	/** One-hour slots from 06:00 to 23:00 venue time on [date], each marked available or not. */
	fun availability(venueId: Long, date: LocalDate): Availability {
		val now = Instant.now(clock)
		val venue = venues.getBookingInfo(venueId)
		val zone = ZoneId.of(venue.timezone)
		val slots = (FIRST_START_HOUR..LAST_START_HOUR).map { hour ->
			val start = date.atTime(LocalTime.of(hour, 0)).atZone(zone).toInstant()
			TimeRange(start, start.plus(Duration.ofHours(1)))
		}
		val booked = reservations.bookedPeriods(venueId, TimeRange(slots.first().start, slots.last().end))
		return Availability(
			venueId = venueId,
			date = date,
			timezone = venue.timezone,
			slots = slots.map { slot ->
				Slot(
					startTime = slot.start,
					localTime = slot.start.atZone(zone).toLocalTime().toString(),
					available = slot.start > now && booked.none { it.overlaps(slot) },
				)
			},
		)
	}
}

private const val MAX_PAGE_SIZE = 100

data class Availability(val venueId: Long, val date: LocalDate, val timezone: String, val slots: List<Slot>)

/** [startTime] is UTC; [localTime] is the same moment in the venue's zone, e.g. "18:00". */
data class Slot(val startTime: Instant, val localTime: String, val available: Boolean)
