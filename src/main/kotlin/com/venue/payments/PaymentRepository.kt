package com.venue.payments

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.math.BigDecimal

@Repository
class PaymentRepository(private val jdbc: JdbcTemplate) {

	/** Throws on a duplicate event_id (SQLState 23505) if another request recorded it first. */
	fun insert(eventId: String, reservationId: Long, amount: BigDecimal, outcome: PaymentOutcome) {
		jdbc.update(
			"insert into payments (event_id, reservation_id, amount, outcome) values (?, ?, ?, ?)",
			eventId,
			reservationId,
			amount,
			outcome.name,
		)
	}

	fun findOutcome(eventId: String): PaymentOutcome? = jdbc.query(
		"select outcome from payments where event_id = ?",
		{ rs, _ -> PaymentOutcome.valueOf(rs.getString("outcome")) },
		eventId,
	).firstOrNull()
}
