package com.venue.payments

import com.venue.IntegrationTest
import com.venue.reservations.HOLD_DURATION
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The payment webhook: signatures, idempotency, and how payments change reservations. */
class PaymentWebhookTest : IntegrationTest() {

	@Test
	fun `payment after expiry is not confirmed`() {
		val host = signUp("Host")
		val guest = signUp("Guest")
		val venueId = createVenue(host, rate = "100")
		val reservationId = reserve(guest, venueId, slot(daysAhead = 3, hour = 18, hours = 2)).field("id").asLong()
		clock.advance(HOLD_DURATION + Duration.ofSeconds(1))

		val receipt = webhook(eventId = "evt-late", reservationId = reservationId, amount = "200.00")

		assertEquals(200, receipt.status)
		assertEquals("REFUND_REQUIRED", receipt.field("outcome").asString())
		assertEquals("EXPIRED", get("/api/v1/reservations/$reservationId", guest).field("status").asString())
	}

	@Test
	fun `payment within the hold confirms the reservation`() {
		val host = signUp("Host")
		val guest = signUp("Guest")
		val venueId = createVenue(host, rate = "100")
		val reservationId = reserve(guest, venueId, slot(daysAhead = 3, hour = 18, hours = 2)).field("id").asLong()

		val receipt = webhook(eventId = "evt-paid", reservationId = reservationId, amount = "200.00")

		assertEquals("CONFIRMED", receipt.field("outcome").asString())
		val reservation = get("/api/v1/reservations/$reservationId", guest)
		assertEquals("CONFIRMED", reservation.field("status").asString())
		assertTrue(reservation.field("expiresAt").isNull)
	}

	@Test
	fun `duplicate webhook is idempotent`() {
		val host = signUp("Host")
		val guest = signUp("Guest")
		val venueId = createVenue(host, rate = "100")
		val reservationId = reserve(guest, venueId, slot(daysAhead = 3, hour = 18, hours = 2)).field("id").asLong()

		val first = webhook(eventId = "evt-1", reservationId = reservationId, amount = "200.00")
		val second = webhook(eventId = "evt-1", reservationId = reservationId, amount = "200.00")

		assertEquals("CONFIRMED", first.field("outcome").asString())
		assertFalse(first.field("duplicate").asBoolean())
		assertEquals("CONFIRMED", second.field("outcome").asString())
		assertTrue(second.field("duplicate").asBoolean())
		assertEquals(1, jdbc.queryForObject("select count(*) from payments", Int::class.java))
	}

	@Test
	fun `webhook with a wrong signature is rejected`() {
		val response = post(
			"/api/v1/payments/webhook",
			"""{"eventId":"evt","reservationId":1,"amount":1,"status":"SUCCEEDED"}""",
			headers = mapOf("X-Signature" to "not-the-signature"),
		)
		assertEquals(401, response.status)
		assertEquals("INVALID_SIGNATURE", response.field("code").asString())
	}

	@Test
	fun `a payment for an unknown reservation is acknowledged and ignored`() {
		val receipt = webhook(eventId = "evt-unknown", reservationId = 999_999, amount = "100.00")
		assertEquals(200, receipt.status)
		assertEquals("IGNORED", receipt.field("outcome").asString())
		assertEquals(0, jdbc.queryForObject("select count(*) from payments", Int::class.java))

		val blankId = webhook(eventId = " ", reservationId = 1, amount = "100.00")
		assertEquals(400, blankId.status)
		assertEquals("eventId must be 1 to 100 characters.", blankId.field("message").asString())
	}
}
