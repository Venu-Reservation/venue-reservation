package com.venue.reservations

import com.venue.common.ConflictException
import com.venue.common.ForbiddenException
import com.venue.common.NotFoundException
import com.venue.common.TimeRange
import com.venue.common.isDeadlock
import com.venue.common.isExclusionViolation
import com.venue.common.validate
import com.venue.users.UserService
import com.venue.venues.VenueService
import org.springframework.dao.DataAccessException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId


@Service
class DefaultReservationService(
	private val reservations: ReservationRepository,
	private val venues: VenueService,
	private val users: UserService,
	private val clock: Clock,
	private val transactions: TransactionTemplate,
) : ReservationService {

	@Transactional(readOnly = true)
	override fun venuesBookedDuring(range: TimeRange): Set<Long> =
		reservations.venuesBookedDuring(range, Instant.now(clock))

	@Transactional(readOnly = true)
	override fun bookedPeriods(venueId: Long, range: TimeRange): List<TimeRange> =
		reservations.bookedPeriods(venueId, range, Instant.now(clock))

	// Not @Transactional: each attempt below runs in its own transaction.
	override fun reserve(userId: Long, venueId: Long?, startTime: Instant?, endTime: Instant?): ReservationResponse {
		validate(venueId != null && startTime != null && endTime != null) { "venueId, startTime and endTime are required." }
		validate(startTime!! < endTime!!) { "startTime must be before endTime." }
		val range = TimeRange(startTime, endTime)
		return try {
			transactions.execute { reserveOnce(userId, venueId!!, range) }!!
		} catch (e: DataAccessException) {
			// The hold-expiry job and this booking can expire the same stale holds at
			// once, lock them in different orders and deadlock. Postgres then aborts one
			// transaction; if it was ours, try once more in a fresh one.
			if (!isDeadlock(e)) throw e
			transactions.execute { reserveOnce(userId, venueId!!, range) }!!
		}
	}

	private fun reserveOnce(userId: Long, venueId: Long, range: TimeRange): ReservationResponse {
		val now = Instant.now(clock)
		val venue = venues.getBookingInfo(venueId)
		val hours = validateBooking(range, ZoneId.of(venue.timezone), now)
		val totalPrice = venue.hourlyRate.multiply(BigDecimal.valueOf(hours))

		// The exclusion constraint can't compare against the time, so lapsed holds
		// still count until they're marked EXPIRED. Clear this venue's first.
		reservations.expireStaleHolds(venueId, now)
		val id = try {
			reservations.insertHold(venueId, userId, range, totalPrice, expiresAt = now + HOLD_DURATION)
		} catch (e: DataIntegrityViolationException) {
			// Only an exclusion violation (23P01) means "already booked".
			if (!isExclusionViolation(e)) throw e
			throw ConflictException("RESERVATION_CONFLICT", "Those hours are already booked. Pick another time.")
		}
		return toResponses(listOf(reservations.find(id)!!)).single()
	}

	@Transactional(readOnly = true)
	override fun get(userId: Long, reservationId: Long): ReservationResponse {
		val row = reservations.find(reservationId) ?: throw notFound()
		val hostId = venues.findSummaries(listOf(row.venueId)).getValue(row.venueId).hostId
		if (row.renterId != userId && hostId != userId) {
			throw ForbiddenException("Only the guest or the venue's host can see this reservation.")
		}
		return toResponses(listOf(row)).single()
	}

	@Transactional(readOnly = true)
	override fun listForRenter(userId: Long, period: ListPeriod?): List<ReservationResponse> =
		toResponses(reservations.findByRenter(userId, period, Instant.now(clock)))

	@Transactional(readOnly = true)
	override fun listForHost(userId: Long, period: ListPeriod?): List<ReservationResponse> =
		toResponses(reservations.findByVenues(venues.findIdsHostedBy(userId), period, Instant.now(clock)))

	@Transactional
	override fun cancel(userId: Long, reservationId: Long): ReservationResponse {
		val row = reservations.findForUpdate(reservationId) ?: throw notFound()
		if (row.renterId != userId) throw ForbiddenException("Only the guest who booked can cancel this reservation.")

		val now = Instant.now(clock)
		when (row.effectiveStatus(now)) {
			ReservationStatus.CANCELLED -> {}
			ReservationStatus.EXPIRED -> throw notCancellable("This hold has already expired.")
			ReservationStatus.PENDING, ReservationStatus.CONFIRMED -> {
				if (row.period.start <= now) throw notCancellable("This booking has already started.")
				reservations.updateStatus(row.id, ReservationStatus.CANCELLED)
			}
		}
		return toResponses(listOf(reservations.find(row.id)!!)).single()
	}

	@Transactional
	override fun expireStaleHolds(): Int = reservations.expireStaleHolds(venueId = null, Instant.now(clock))

	@Transactional
	override fun applyPayment(reservationId: Long, amount: BigDecimal): PaymentResult {
		// Locked so a cancel or a second payment can't interleave with this one.
		val row = reservations.findForUpdate(reservationId) ?: throw notFound()
		val status = row.effectiveStatus(Instant.now(clock))

		if (status == ReservationStatus.PENDING && amount.compareTo(row.totalPrice) == 0) {
			reservations.updateStatus(row.id, ReservationStatus.CONFIRMED)
			return PaymentResult.CONFIRMED
		}
		// Paid too late: never confirm, and record that the hold lapsed.
		if (status == ReservationStatus.EXPIRED && row.status == ReservationStatus.PENDING) {
			reservations.updateStatus(row.id, ReservationStatus.EXPIRED)
		}
		// Expired, wrong amount, already confirmed (a second payment), or cancelled.
		return PaymentResult.REFUND_REQUIRED
	}

	@Transactional(readOnly = true)
	override fun getPaymentDetails(reservationId: Long): PaymentDetails {
		val row = reservations.find(reservationId) ?: throw notFound()
		return PaymentDetails(row.id, row.renterId, row.totalPrice)
	}

	/** Checks the shared booking rules in the venue's zone and returns the number of hours. */
	private fun validateBooking(range: TimeRange, zone: ZoneId, now: Instant): Long {
		val localStart = range.start.atZone(zone)
		validate(range.start > now) { "Pick a time in the future." }
		validate(localStart.minute == 0 && localStart.second == 0 && localStart.nano == 0) {
			"Bookings start on the hour."
		}
		validate(localStart.hour in FIRST_START_HOUR..LAST_START_HOUR) {
			"Bookings start between 6:00 AM and 11:00 PM, venue time."
		}
		val duration = Duration.between(range.start, range.end)
		validate(!duration.isNegative && duration.toMinutesPart() == 0 && duration.toSecondsPart() == 0) {
			"Bookings last a whole number of hours."
		}
		val hours = duration.toHours()
		validate(hours in MIN_BOOKING_HOURS..MAX_BOOKING_HOURS) {
			"Bookings last between $MIN_BOOKING_HOURS and $MAX_BOOKING_HOURS hours."
		}
		return hours
	}

	private fun toResponses(rows: List<ReservationRow>): List<ReservationResponse> {
		if (rows.isEmpty()) return emptyList()
		val venueSummaries = venues.findSummaries(rows.map { it.venueId }.toSet())
		val renterNames = users.findNames(rows.map { it.renterId }.toSet())
		val now = Instant.now(clock)
		return rows.map { row ->
			val venue = venueSummaries.getValue(row.venueId)
			val status = row.effectiveStatus(now)
			ReservationResponse(
				id = row.id,
				venue = ReservationVenue(venue.id, venue.name, venue.city, venue.state, venue.timezone, venue.thumbnailUrl),
				renter = ReservationRenter(row.renterId, renterNames[row.renterId].orEmpty()),
				startTime = row.period.start,
				endTime = row.period.end,
				hours = Duration.between(row.period.start, row.period.end).toHours(),
				totalPrice = row.totalPrice,
				status = status,
				expiresAt = row.expiresAt.takeIf { status == ReservationStatus.PENDING },
				createdAt = row.createdAt,
			)
		}
	}

	private fun notFound() = NotFoundException("Reservation not found.")

	private fun notCancellable(message: String) = ConflictException("RESERVATION_NOT_CANCELLABLE", message)
}
