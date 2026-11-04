package com.venue.reservation.controller


import com.venue.reservation.venues.VenueService
import org.springframework.web.bind.annotation.*


@RestController
@RequestMapping("/api/v1/venues")
class VenueController (private val venueService: VenueService){

    @GetMapping("/search")
    fun searchVenuesStub(): Map<String, String> =venueService.getSearchStub()

    @PostMapping
    fun createVenueStub(): Map<String, String> = venueService.createVenueStub()

    @PutMapping("/{id}")
    fun updateVenueStub(@PathVariable id: Long): Map<String, String> = venueService.updateVenueStub(id)

    @PatchMapping("/{id}")
    fun patchVenueStub(@PathVariable id: Long): Map<String, String> = venueService.patchVenueStub(id)

    @DeleteMapping("/{id}")
    fun deleteVenueStub(@PathVariable id: Long): Map<String, String> = venueService.deleteVenueStub(id)
}