package com.venue.reservation.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "reservations")
class Reservation(
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "venue_id", nullable = false)
	var venue: Venue,

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "renter_id", nullable = false)
	var renter: User,

	@Column(name = "start_time", nullable = false)
	var startTime: Instant,

	@Column(name = "end_time", nullable = false)
	var endTime: Instant,

	@Column(name = "total_price", nullable = false, precision = 10, scale = 2)
	var totalPrice: BigDecimal,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	var status: ReservationStatus = ReservationStatus.PENDING,

	@Column(name = "created_at", nullable = false, updatable = false)
	val createdAt: Instant = Instant.now(),

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	val id: Long? = null,
) {
	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other !is Reservation) return false
		return id != null && id == other.id
	}

	override fun hashCode(): Int = Reservation::class.hashCode()

	override fun toString(): String =
		"Reservation(id=$id, startTime=$startTime, endTime=$endTime, totalPrice=$totalPrice, status=$status)"
}
