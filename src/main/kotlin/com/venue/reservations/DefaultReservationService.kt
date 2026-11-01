package com.venue.reservations

import com.venue.common.TimeRange
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DefaultReservationService(private val reservations: ReservationRepository) : ReservationService {

	@Transactional(readOnly = true)
	override fun venuesBookedDuring(range: TimeRange): Set<Long> = reservations.venuesBookedDuring(range)

	@Transactional(readOnly = true)
	override fun bookedPeriods(venueId: Long, range: TimeRange): List<TimeRange> =
		reservations.bookedPeriods(venueId, range)
}
