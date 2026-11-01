package com.venue.reservations

enum class ReservationStatus {
	/** Waiting for payment, held until expires_at (10 minutes). */
	PENDING,

	/** Paid within the hold. */
	CONFIRMED,

	/** Cancelled by the renter. */
	CANCELLED,

	/** The hold lapsed without payment. */
	EXPIRED,
}
