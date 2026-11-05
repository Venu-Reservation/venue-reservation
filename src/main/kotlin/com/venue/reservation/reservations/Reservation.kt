package com.venue.reservation.reservations

import jakarta.persistence.*
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "reservations")
class Reservation(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID = UUID.randomUUID(),

    @Column(name = "venue_id", nullable = false)
    var venueId: UUID,

    @Column(name = "start_time", nullable = false)
    var startTime: OffsetDateTime,

    @Column(name = "end_time", nullable = false)
    var endTime: OffsetDateTime,

    @Column(nullable = false)
    var status: String
)