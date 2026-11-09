package com.venue.reservation.venues

import org.springframework.stereotype.Service
import java.util.UUID

@Service
class VenueService(private val venueRepository: VenueRepository) {
    fun getSearchStub(): Map<String, String> = mapOf(
        "endpoint" to "GET /api/v1/venues/search",
        "status" to "STUB"
    )

    fun createVenueStub(): Map<String, String> = mapOf(
        "message" to "Venue created stub"
    )

    fun updateVenueStub(id: UUID): Map<String, String> = mapOf(
        "message" to "Venue updated stub"
    )

    fun patchVenueStub(id: UUID): Map<String, String> = mapOf(
        "message" to "Venue partially updated stub"
    )

    fun deleteVenueStub(id: UUID): Map<String, String> = mapOf(
        "message" to "Venue deleted stub "
    )
}