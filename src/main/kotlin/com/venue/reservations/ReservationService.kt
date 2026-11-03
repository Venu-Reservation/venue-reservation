package com.venue.reservations

import com.venue.common.TimeRange
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

/** How long a PENDING reservation waits for payment. */
val HOLD_DURATION: Duration = Duration.ofMinutes(10)

// The shared booking rules (docs/project.md): start on the hour between
// these local venue times (inclusive), and last a whole number of hours.
const val FIRST_START_HOUR = 6
const val LAST_START_HOUR = 23
const val MIN_BOOKING_HOURS = 1L
const val MAX_BOOKING_HOURS = 12L

/**
 * The reservations module's API for the rest of the app.
 *
 * "Blocking" reservations are CONFIRMED ones and PENDING ones whose hold
 * hasn't expired yet. Those are the ones that make a venue unavailable.
 */
interface ReservationService {
	/** Ids of venues with a blocking reservation overlapping [range]. */
	fun venuesBookedDuring(range: TimeRange): Set<Long>

	/** Blocking reservation periods for one venue that overlap [range]. */
	fun bookedPeriods(venueId: Long, range: TimeRange): List<TimeRange>

	/**
	 * Validates and books [startTime, endTime) at a venue for [userId] as a PENDING
	 * hold that expires in 10 minutes. Throws a 409 RESERVATION_CONFLICT if the time is taken.
	 */
	fun reserve(userId: Long, venueId: Long?, startTime: Instant?, endTime: Instant?): ReservationResponse

	/** The renter or the venue's host can view; anyone else gets a 403. */
	fun get(userId: Long, reservationId: Long): ReservationResponse

	/** The user's own bookings. */
	fun listForRenter(userId: Long, period: ListPeriod?): List<ReservationResponse>

	/** Bookings on venues the user hosts. */
	fun listForHost(userId: Long, period: ListPeriod?): List<ReservationResponse>

	/** Renter only. Cancelling twice is fine; an expired or started booking is a 409. */
	fun cancel(userId: Long, reservationId: Long): ReservationResponse

	/** Marks PENDING holds past their expiry as EXPIRED. Returns how many changed. */
	fun expireStaleHolds(): Int

	/**
	 * Applies a successful payment of [amount]. Confirms the reservation only if
	 * it's still within its hold and the amount matches; otherwise the money
	 * must be refunded. Call inside the payment's transaction.
	 */
	fun applyPayment(reservationId: Long, amount: BigDecimal): PaymentResult

	/** Who booked a reservation and what it costs. Throws a 404 if missing. */
	fun getPaymentDetails(reservationId: Long): PaymentDetails
}

enum class ListPeriod {
	/** Not over yet and still active (CONFIRMED, or PENDING within its hold). Soonest first. */
	UPCOMING,

	/** Everything else: finished, cancelled or expired. Most recent first. */
	PAST,
}

enum class PaymentResult { CONFIRMED, REFUND_REQUIRED }

data class PaymentDetails(val reservationId: Long, val renterId: Long, val totalPrice: BigDecimal)

/** The reservation JSON the API returns. Times are UTC; show them in venue.timezone. */
data class ReservationResponse(
	val id: Long,
	val venue: ReservationVenue,
	val renter: ReservationRenter,
	val startTime: Instant,
	val endTime: Instant,
	val hours: Long,
	val totalPrice: BigDecimal,
	val status: ReservationStatus,
	/** When a PENDING hold lapses. Null once the reservation is no longer pending. */
	val expiresAt: Instant?,
	val createdAt: Instant,
)

data class ReservationVenue(
	val id: Long,
	val name: String,
	val city: String,
	val state: String,
	val timezone: String,
	val thumbnailUrl: String?,
)

data class ReservationRenter(val id: Long, val name: String)
