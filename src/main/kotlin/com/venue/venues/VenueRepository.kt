package com.venue.venues

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface VenueRepository : JpaRepository<Venue, Long> {
	fun existsByHostIdAndDeletedAtIsNull(hostId: Long): Boolean

	fun findByIdAndDeletedAtIsNull(id: Long): Venue?

	fun findByHostIdAndDeletedAtIsNullOrderByCreatedAtDesc(hostId: Long): List<Venue>

	fun findByCityIgnoreCaseAndDeletedAtIsNull(city: String, pageable: Pageable): Page<Venue>

	@Query(
		"""
		select v from Venue v
		where lower(v.city) = lower(:city) and v.deletedAt is null and v.id not in :excludedIds
		""",
	)
	fun searchExcluding(city: String, excludedIds: Collection<Long>, pageable: Pageable): Page<Venue>
}
