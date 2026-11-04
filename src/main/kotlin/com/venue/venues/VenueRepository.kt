package com.venue.venues

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface VenueRepository : JpaRepository<Venue, Long> {
	fun existsByHostIdAndDeletedAtIsNull(hostId: Long): Boolean

	fun findByIdAndDeletedAtIsNull(id: Long): Venue?

	/**
	 * Like [findByIdAndDeletedAtIsNull], with `for share`: many bookings can hold
	 * it at once, but a delete (`for update`) waits for them, and they for it.
	 */
	@Lock(LockModeType.PESSIMISTIC_READ)
	@Query("select v from Venue v where v.id = :id and v.deletedAt is null")
	fun findActiveForShare(id: Long): Venue?

	/** Like [findByIdAndDeletedAtIsNull], with `for update`: no booking can start until this transaction ends. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select v from Venue v where v.id = :id and v.deletedAt is null")
	fun findActiveForUpdate(id: Long): Venue?

	fun findByHostIdAndDeletedAtIsNullOrderByCreatedAtDesc(hostId: Long): List<Venue>

	fun findByCityIgnoreCaseAndDeletedAtIsNullAndPausedFalse(city: String, pageable: Pageable): Page<Venue>

	@Query(
		"""
		select v from Venue v
		where lower(v.city) = lower(:city) and v.deletedAt is null and v.paused = false and v.id not in :excludedIds
		""",
	)
	fun searchExcluding(city: String, excludedIds: Collection<Long>, pageable: Pageable): Page<Venue>
}
