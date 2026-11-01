package com.venue.venues

/** The venues module's API for the rest of the app. */
interface VenueService {
	/** True if the user hosts at least one active (not deleted) venue. */
	fun hostsAnyVenue(userId: Long): Boolean
}
