package com.venue.venues

import com.venue.common.ConflictException
import com.venue.reservations.ReservationService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Deleting a venue needs both modules: venues owns the row, reservations knows
 * the bookings. It can't live in DefaultVenueService, because DefaultReservationService
 * already depends on VenueService and Spring can't build two beans that need each other.
 */
@Service
class VenueDeletionService(
	private val venues: VenueService,
	private val reservations: ReservationService,
) {
	/** Owner only. A 409 VENUE_HAS_BOOKINGS while the venue has upcoming bookings. */
	@Transactional
	fun delete(userId: Long, venueId: Long) {
		// Locked first, so no booking can be made between the check and the delete.
		venues.lockOwnedForDelete(userId, venueId)
		if (reservations.hasUpcomingBookings(venueId)) {
			throw ConflictException(
				"VENUE_HAS_BOOKINGS",
				"This venue has upcoming bookings. Pause it to stop new ones, and delete it once they're over.",
			)
		}
		venues.delete(userId, venueId)
	}
}
