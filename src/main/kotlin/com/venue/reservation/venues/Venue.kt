package com.venue.reservation.venues

import jakarta.persistence.*
import java.util.UUID

    @Entity
    @Table(name = "venues")
    class Venue(
        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
//        var id: UUID = UUID.randomUUID(),
        var id: UUID? = null,


        @Column(nullable = false)
        var name: String,

        @Column(nullable = false)
        var timezone: String
    )