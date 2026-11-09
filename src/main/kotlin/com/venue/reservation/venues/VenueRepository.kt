package com.venue.reservation.venues

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface VenueRepository : JpaRepository<Venue, UUID>