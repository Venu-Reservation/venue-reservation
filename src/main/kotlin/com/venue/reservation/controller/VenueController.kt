package com.venue.reservation.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/venues")
class VenueController {

    @GetMapping("/search")
    fun searchVenuesStub(): Map<String, String> = mapOf(
        "endpoint" to "GET /api/v1/venues/search",
        "status" to "STUB"
    )
}