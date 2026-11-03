package com.venue.venues

import com.venue.common.PageResponse
import java.math.BigDecimal
import java.time.Instant

/** The venues module's API for the rest of the app. Deleted venues are never returned unless stated. */
interface VenueService {
	/** True if the user hosts at least one active venue. */
	fun hostsAnyVenue(userId: Long): Boolean

	/** Validates [request] and lists the venue with [hostId] as its host. */
	fun create(hostId: Long, request: VenueRequest): VenueResponse

	/** Owner only: 404 if missing or deleted, 403 for anyone else. Validates like [create]. */
	fun update(userId: Long, venueId: Long, request: VenueRequest): VenueResponse

	/** Soft-deletes. Owner only, like [update]. */
	fun delete(userId: Long, venueId: Long)

	/** Throws a 404 if missing or deleted. */
	fun get(venueId: Long): VenueResponse

	fun listHostedBy(hostId: Long): List<VenueResponse>

	/** Active venues in [city] (case-insensitive), leaving out [excludedIds]. */
	fun search(city: String, excludedIds: Set<Long>, page: Int, size: Int): PageResponse<VenueResponse>

	/** What booking a venue needs. Throws a 404 if missing or deleted. */
	fun getBookingInfo(venueId: Long): VenueBookingInfo

	/** Short descriptions for showing reservations, including deleted venues. */
	fun findSummaries(venueIds: Collection<Long>): Map<Long, VenueSummary>

	/** Ids of the active venues [hostId] hosts. */
	fun findIdsHostedBy(hostId: Long): List<Long>
}

/**
 * Request body for POST and PUT, as sent. Fields are nullable so a missing one
 * gets a clear validation message. There's no hostId (the host is the caller),
 * no state or timezone (they come from the city) and no coordinates (geocoding, later).
 */
data class VenueRequest(
	val name: String?,
	val capacity: Int?,
	val sizeSqft: Int?,
	val addressLine1: String?,
	val addressLine2: String?,
	val city: String?,
	val postalCode: String?,
	val hourlyRate: BigDecimal?,
	val imageUrls: List<String>?,
)

/** The venue JSON the API returns. */
data class VenueResponse(
	val id: Long,
	val hostId: Long,
	val name: String,
	val capacity: Int,
	val sizeSqft: Int?,
	val addressLine1: String,
	val addressLine2: String?,
	val city: String,
	val state: String,
	val postalCode: String,
	val timezone: String,
	val latitude: BigDecimal?,
	val longitude: BigDecimal?,
	val hourlyRate: BigDecimal,
	val thumbnailUrl: String?,
	val images: List<VenueImageResponse>,
	val createdAt: Instant,
)

data class VenueImageResponse(val id: Long, val imageUrl: String, val displayOrder: Int)

data class VenueBookingInfo(val id: Long, val hostId: Long, val timezone: String, val hourlyRate: BigDecimal)

data class VenueSummary(
	val id: Long,
	val hostId: Long,
	val name: String,
	val city: String,
	val state: String,
	val timezone: String,
	val thumbnailUrl: String?,
)
