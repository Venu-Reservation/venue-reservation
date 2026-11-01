package com.venue.venues

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DefaultVenueService(private val venues: VenueRepository) : VenueService {

	@Transactional(readOnly = true)
	override fun hostsAnyVenue(userId: Long): Boolean = venues.existsByHostIdAndDeletedAtIsNull(userId)
}
