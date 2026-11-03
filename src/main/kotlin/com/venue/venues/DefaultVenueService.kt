package com.venue.venues

import com.venue.common.ForbiddenException
import com.venue.common.NotFoundException
import com.venue.common.PageResponse
import com.venue.common.validate
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
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
	override fun create(hostId: Long, request: VenueRequest): VenueResponse {
		val input = request.validated()
		val venue = Venue(
			hostId = hostId,
			name = input.name,
			capacity = input.capacity,
			sizeSqft = input.sizeSqft,
			addressLine1 = input.addressLine1,
			addressLine2 = input.addressLine2,
			city = input.city.displayName,
			state = input.city.state,
			postalCode = input.postalCode,
			timezone = input.city.timezone,
			hourlyRate = input.hourlyRate,
		)
		venue.replaceImages(input.imageUrls)
		return venues.saveAndFlush(venue).toResponse()
	}

	@Transactional
	override fun update(userId: Long, venueId: Long, request: VenueRequest): VenueResponse {
		val venue = findOwned(userId, venueId)
		val input = request.validated()
		val addressChanged = input.addressLine1 != venue.addressLine1 || input.addressLine2 != venue.addressLine2 ||
			input.city.displayName != venue.city || input.postalCode != venue.postalCode
		venue.name = input.name
		venue.capacity = input.capacity
		venue.sizeSqft = input.sizeSqft
		venue.addressLine1 = input.addressLine1
		venue.addressLine2 = input.addressLine2
		venue.city = input.city.displayName
		venue.state = input.city.state
		venue.postalCode = input.postalCode
		venue.timezone = input.city.timezone
		venue.hourlyRate = input.hourlyRate
		venue.replaceImages(input.imageUrls)
		// Coordinates belong to the old address; geocoding (later) fills them in again.
		if (addressChanged) {
			venue.latitude = null
			venue.longitude = null
		}
		return venues.saveAndFlush(venue).toResponse()
	}

	@Transactional
	override fun delete(userId: Long, venueId: Long) {
		findOwned(userId, venueId).deletedAt = Instant.now(clock)
	}

	@Transactional
	override fun setPaused(userId: Long, venueId: Long, paused: Boolean): VenueResponse {
		val venue = findOwned(userId, venueId)
		venue.paused = paused
		return venue.toResponse()
	}

	@Transactional
	override fun lockOwnedForDelete(userId: Long, venueId: Long) {
		val venue = venues.findActiveForUpdate(venueId) ?: throw notFound()
		if (venue.hostId != userId) throw notYours()
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
			venues.findByCityIgnoreCaseAndDeletedAtIsNullAndPausedFalse(city, pageable)
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
	override fun getBookingInfo(venueId: Long): VenueBookingInfo = findActive(venueId).toBookingInfo()

	// Not read-only: Postgres refuses "for share" in a read-only transaction.
	@Transactional
	override fun getBookingInfoLocked(venueId: Long): VenueBookingInfo =
		(venues.findActiveForShare(venueId) ?: throw notFound()).toBookingInfo()

	@Transactional(readOnly = true)
	override fun findSummaries(venueIds: Collection<Long>): Map<Long, VenueSummary> =
		venues.findAllById(venueIds).associate {
			val id = it.id!!
			id to VenueSummary(id, it.hostId, it.name, it.city, it.state, it.timezone, it.thumbnailUrl)
		}

	@Transactional(readOnly = true)
	override fun findIdsHostedBy(hostId: Long): List<Long> =
		venues.findByHostIdAndDeletedAtIsNullOrderByCreatedAtDesc(hostId).map { it.id!! }

	private fun findActive(venueId: Long): Venue = venues.findByIdAndDeletedAtIsNull(venueId) ?: throw notFound()

	private fun findOwned(userId: Long, venueId: Long): Venue {
		val venue = findActive(venueId)
		if (venue.hostId != userId) throw notYours()
		return venue
	}

	private fun notFound() = NotFoundException("Venue not found.")

	private fun notYours() = ForbiddenException("Only the venue's host can change it.")

	private fun Venue.toBookingInfo() = VenueBookingInfo(id!!, hostId, timezone, hourlyRate, paused)

	private fun Venue.toResponse() = VenueResponse(
		id = id!!,
		hostId = hostId,
		name = name,
		capacity = capacity,
		sizeSqft = sizeSqft,
		addressLine1 = addressLine1,
		addressLine2 = addressLine2,
		city = city,
		state = state,
		postalCode = postalCode,
		timezone = timezone,
		latitude = latitude,
		longitude = longitude,
		hourlyRate = hourlyRate,
		thumbnailUrl = thumbnailUrl,
		images = images.map { VenueImageResponse(it.id!!, it.imageUrl, it.displayOrder) },
		paused = paused,
		createdAt = createdAt,
	)
}

/** A request that passed validation, with clean values. */
private class VenueInput(
	val name: String,
	val capacity: Int,
	val sizeSqft: Int?,
	val addressLine1: String,
	val addressLine2: String?,
	val city: SupportedCity,
	val postalCode: String,
	val hourlyRate: BigDecimal,
	val imageUrls: List<String>,
)

private const val MAX_IMAGES = 20
private val MAX_RATE = BigDecimal("99999999.99")
private val IMAGE_URL = Regex("""^https?://\S+$""")

/** Applies the shared validation rules (docs/project.md) and returns clean values. */
private fun VenueRequest.validated(): VenueInput {
	val name = name?.trim().orEmpty()
	val addressLine1 = addressLine1?.trim().orEmpty()
	val addressLine2 = addressLine2?.trim()?.ifEmpty { null }
	val postalCode = postalCode?.trim().orEmpty()
	val imageUrls = imageUrls.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }

	validate(name.isNotEmpty()) { "Name is required." }
	validate(name.length <= 255) { "Name must be 255 characters or fewer." }
	validate(capacity != null && capacity >= 1) { "Capacity must be a whole number of at least 1." }
	validate(sizeSqft == null || sizeSqft >= 1) { "Size must be a whole number of at least 1." }
	validate(addressLine1.isNotEmpty() && postalCode.isNotEmpty()) { "Street address and postal code are required." }
	validate(addressLine1.length <= 255 && (addressLine2?.length ?: 0) <= 255) {
		"Address lines must be 255 characters or fewer."
	}
	validate(postalCode.length <= 20) { "Postal code must be 20 characters or fewer." }
	val city = city?.let { SupportedCity.find(it) }
	validate(city != null) {
		"City must be one of: ${SupportedCity.entries.joinToString { it.displayName }}."
	}
	val rate = hourlyRate
	validate(rate != null && rate > BigDecimal.ZERO && rate.stripTrailingZeros().scale() <= 2 && rate <= MAX_RATE) {
		"Hourly rate must be more than 0, with at most 2 decimal places."
	}
	validate(imageUrls.size <= MAX_IMAGES) { "A venue can have at most $MAX_IMAGES photos." }
	validate(imageUrls.all { it.length <= 500 && IMAGE_URL.matches(it) }) {
		"Photo links must be http(s) URLs of 500 characters or fewer."
	}

	return VenueInput(
		name, capacity!!, sizeSqft, addressLine1, addressLine2, city!!, postalCode, rate!!.setScale(2), imageUrls,
	)
}
