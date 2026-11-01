package com.venue.reservation.controller

import org.springframework.web.bind.annotation.*


@RestController
@RequestMapping("/api/v1/venues")
class VenueController {

    @GetMapping("/search")
    fun searchVenuesStub(): Map<String, String> = mapOf(
        "endpoint" to "GET /api/v1/venues/search",
        "status" to "STUB"
    )

    @PostMapping
    fun createVenueStub(): Map<String, String> = mapOf(
        "message" to "Venue created stub"
    )

    @PutMapping("/{id}")
    fun updateVenueStub(@PathVariable id: Long): Map<String, String> = mapOf(
        "message" to "Venue updated stub"
    )

    @PatchMapping("/{id}")
    fun patchVenueStub(@PathVariable id: Long): Map<String, String> = mapOf(
        "message" to "Venue partially updated stub"
    )

    @DeleteMapping("/{id}")
    fun deleteVenueStub(@PathVariable id: Long): Map<String, String> = mapOf(
        "message" to "Venue deleted stub"
    )
}