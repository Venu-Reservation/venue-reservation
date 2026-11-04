package com.venue.reservations

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** Every minute, marks PENDING holds past their 10 minutes as EXPIRED. */
@Component
class HoldExpiryJob(private val reservations: ReservationService) {
	private val log = LoggerFactory.getLogger(javaClass)

	@Scheduled(fixedDelayString = "PT1M")
	fun expireStaleHolds() {
		val expired = reservations.expireStaleHolds()
		if (expired > 0) log.info("Expired {} unpaid reservation hold(s)", expired)
	}
}
