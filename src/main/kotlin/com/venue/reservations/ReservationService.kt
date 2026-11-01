package com.venue.reservations

import com.venue.common.TimeRange

/**
 * The reservations module's API for the rest of the app.
 *
 * "Blocking" reservations are CONFIRMED ones and PENDING ones whose hold
 * hasn't expired yet. Those are the ones that make a venue unavailable.
 */
interface ReservationService {
	/** Ids of venues with a blocking reservation overlapping [range]. */
	fun venuesBookedDuring(range: TimeRange): Set<Long>

	/** Blocking reservation periods for one venue that overlap [range]. */
	fun bookedPeriods(venueId: Long, range: TimeRange): List<TimeRange>
}
