package com.venue.venues

import com.venue.common.ForbiddenException
import com.venue.common.NotFoundException
import com.venue.common.PageResponse
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

@Service
class DefaultVenueService(
	private val venues: VenueRepository,
	private val clock: Clock,
) : VenueService {

	@Transactional(readOnly = true)
	override fun hostsAnyVenue(userId: Long): Boolean = venues.existsByHostIdAndDeletedAtIsNull(userId)

	@Transactional
	override fun create(hostId: Long, input: VenueInput): VenueResponse {
		val venue = Venue(
			hostId = hostId,
			name = input.name,
			capacity = input.capacity,
			sizeSqft = input.sizeSqft,
			city = input.city,
			state = input.state,
			timezone = input.timezone,
			hourlyRate = input.hourlyRate,
		)
		venue.replaceImages(input.imageUrls)
		return venues.saveAndFlush(venue).toResponse()
	}

	@Transactional
	override fun update(userId: Long, venueId: Long, input: VenueInput): VenueResponse {
		val venue = findOwned(userId, venueId)
		venue.name = input.name
		venue.capacity = input.capacity
		venue.sizeSqft = input.sizeSqft
		venue.city = input.city
		venue.state = input.state
		venue.timezone = input.timezone
		venue.hourlyRate = input.hourlyRate
		venue.replaceImages(input.imageUrls)
		return venues.saveAndFlush(venue).toResponse()
	}

	@Transactional
	override fun delete(userId: Long, venueId: Long) {
		findOwned(userId, venueId).deletedAt = Instant.now(clock)
	}

	@Transactional(readOnly = true)
	override fun get(venueId: Long): VenueResponse = findActive(venueId).toResponse()

	@Transactional(readOnly = true)
	override fun listHostedBy(hostId: Long): List<VenueResponse> =
		venues.findByHostIdAndDeletedAtIsNullOrderByCreatedAtDesc(hostId).map { it.toResponse() }

	@Transactional(readOnly = true)
	override fun search(city: String, excludedIds: Set<Long>, page: Int, size: Int): PageResponse<VenueResponse> {
		val pageable = PageRequest.of(page, size, Sort.by("createdAt", "id"))
		// "not in" an empty list isn't valid SQL everywhere, so skip it when nothing is excluded.
		val result = if (excludedIds.isEmpty()) {
			venues.findByCityIgnoreCaseAndDeletedAtIsNull(city, pageable)
		} else {
			venues.searchExcluding(city, excludedIds, pageable)
		}
		return PageResponse(
			items = result.content.map { it.toResponse() },
			page = page,
			size = size,
			totalItems = result.totalElements,
			totalPages = result.totalPages,
		)
	}

	@Transactional(readOnly = true)
	override fun getBookingInfo(venueId: Long): VenueBookingInfo {
		val venue = findActive(venueId)
		return VenueBookingInfo(venue.id!!, venue.hostId, venue.timezone, venue.hourlyRate)
	}

	@Transactional(readOnly = true)
	override fun findSummaries(venueIds: Collection<Long>): Map<Long, VenueSummary> =
		venues.findAllById(venueIds).associate {
			val id = it.id!!
			id to VenueSummary(id, it.hostId, it.name, it.city, it.state, it.timezone, it.thumbnailUrl)
		}

	@Transactional(readOnly = true)
	override fun findIdsHostedBy(hostId: Long): List<Long> =
		venues.findByHostIdAndDeletedAtIsNullOrderByCreatedAtDesc(hostId).map { it.id!! }

	private fun findActive(venueId: Long): Venue =
		venues.findByIdAndDeletedAtIsNull(venueId) ?: throw NotFoundException("Venue not found.")

	private fun findOwned(userId: Long, venueId: Long): Venue {
		val venue = findActive(venueId)
		if (venue.hostId != userId) throw ForbiddenException("Only the venue's host can change it.")
		return venue
	}

	private fun Venue.toResponse() = VenueResponse(
		id = id!!,
		hostId = hostId,
		name = name,
		capacity = capacity,
		sizeSqft = sizeSqft,
		city = city,
		state = state,
		timezone = timezone,
		hourlyRate = hourlyRate,
		thumbnailUrl = thumbnailUrl,
		images = images.map { VenueImageResponse(it.id!!, it.imageUrl, it.displayOrder) },
		createdAt = createdAt,
	)
}
