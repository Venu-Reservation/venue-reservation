package com.venue.payments

import com.venue.common.isUniqueViolation
import com.venue.reservations.PaymentResult
import com.venue.reservations.ReservationService
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal

/**
 * Records payment events and applies successful ones to their reservation.
 * Each event is handled once: a repeated event id changes nothing and
 * reports the outcome recorded the first time.
 */
@Service
class PaymentService(
	private val payments: PaymentRepository,
	private val reservations: ReservationService,
	private val transactions: TransactionTemplate,
) {
	fun handle(event: PaymentEvent): PaymentReceipt {
		payments.findOutcome(event.eventId)?.let { return PaymentReceipt(event.eventId, it, duplicate = true) }
		return try {
			transactions.execute { PaymentReceipt(event.eventId, record(event), duplicate = false) }
		} catch (e: DataIntegrityViolationException) {
			// The same event arrived twice at once and the other request recorded it first.
			if (!isUniqueViolation(e)) throw e
			PaymentReceipt(event.eventId, payments.findOutcome(event.eventId)!!, duplicate = true)
		}
	}

	// Runs in one transaction: the reservation update and the payment row
	// are saved together or not at all.
	private fun record(event: PaymentEvent): PaymentOutcome {
		val outcome = if (event.status == PaymentEventStatus.SUCCEEDED) {
			when (reservations.applyPayment(event.reservationId, event.amount)) {
				PaymentResult.CONFIRMED -> PaymentOutcome.CONFIRMED
				PaymentResult.REFUND_REQUIRED -> PaymentOutcome.REFUND_REQUIRED
			}
		} else {
			reservations.getPaymentDetails(event.reservationId) // 404 for an unknown reservation
			PaymentOutcome.IGNORED
		}
		payments.insert(event.eventId, event.reservationId, event.amount, outcome)
		return outcome
	}
}

/** A payment provider event. amount is in dollars with up to 2 decimals. */
data class PaymentEvent(
	val eventId: String,
	val reservationId: Long,
	val amount: BigDecimal,
	val status: PaymentEventStatus,
)

enum class PaymentEventStatus { SUCCEEDED, FAILED }

enum class PaymentOutcome {
	/** The payment confirmed its reservation. */
	CONFIRMED,

	/** Money was taken but the reservation can't be confirmed (expired, cancelled, wrong amount, already paid). */
	REFUND_REQUIRED,

	/** Not a successful payment; nothing changed. */
	IGNORED,
}

data class PaymentReceipt(val eventId: String, val outcome: PaymentOutcome, val duplicate: Boolean)
