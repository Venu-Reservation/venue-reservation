package com.venue

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class VenueApplication

fun main(args: Array<String>) {
    runApplication<VenueApplication>(*args)
}