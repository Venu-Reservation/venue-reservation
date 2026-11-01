package com.venue.venues

import org.springframework.data.jpa.repository.JpaRepository

interface VenueRepository : JpaRepository<Venue, Long> {
	fun existsByHostIdAndDeletedAtIsNull(hostId: Long): Boolean
}
