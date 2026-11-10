package com.venue.reservation.venues

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
//import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase


@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class VenueRepositoryTest {

    @Autowired
    private lateinit var venueRepository: VenueRepository

    @Test
    fun `should save and find venue by id`() {
        val venue = Venue(
            name = "Test Auditorium",
            timezone = "America/New_York"
        )

        val savedVenue = venueRepository.save(venue)
        assertNotNull(savedVenue.id)

        val foundVenue = venueRepository.findById(savedVenue.id!!).orElse(null)
        assertNotNull(foundVenue)
        assertEquals("Test Auditorium", foundVenue?.name)
        assertEquals("America/New_York", foundVenue?.timezone)
    }
}