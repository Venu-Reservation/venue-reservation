package com.venue.reservation.venues

import org.springframework.stereotype.Service

@Service
class VenueService {
    fun getSearchStub(): Map<String, String> = mapOf(
        "endpoint" to "GET /api/v1/venues/search",
        "status" to "STUB"
    )

    fun createVenueStub(): Map<String, String> = mapOf(
        "message" to "Venue created stub"
    )

    fun updateVenueStub(id: Long): Map<String, String> = mapOf(
        "message" to "Venue updated stub"
    )

    fun patchVenueStub(id: Long): Map<String, String> = mapOf(
        "message" to "Venue partially updated stub"
    )

    fun deleteVenueStub(id: Long): Map<String, String> = mapOf(
        "message" to "Venue deleted stub "
    )
}