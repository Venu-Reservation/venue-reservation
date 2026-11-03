package com.venue.reservations

import com.venue.IntegrationTest
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The booking rules the spec requires tests for (docs/project.md, Backend rules). */
class ReservationRulesTest : IntegrationTest() {

	@Test
	fun `concurrent overlapping bookings give exactly one success and one 409`() {
		val host = signUp("Host")
		val guests = listOf(signUp("Guest One"), signUp("Guest Two"))
		val venueId = createVenue(host)
		val slot = slot(daysAhead = 3, hour = 18)

		// Both requests wait at the gate, then hit the database together.
		val gate = CountDownLatch(1)
		val pool = Executors.newFixedThreadPool(2)
		val results = guests.map { guest ->
			pool.submit(
				Callable {
					gate.await()
					reserve(guest, venueId, slot)
				},
			)
		}
		gate.countDown()
		val responses = results.map { it.get() }
		pool.shutdown()

		assertEquals(listOf(201, 409), responses.map { it.status }.sorted())
		assertEquals("RESERVATION_CONFLICT", responses.single { it.status == 409 }.field("code").asString())
	}

	@Test
	fun `back-to-back ranges both succeed`() {
		val host = signUp("Host")
		val guest = signUp("Guest")
		val venueId = createVenue(host)

		val first = reserve(guest, venueId, slot(daysAhead = 3, hour = 14, hours = 2)) // 14:00-16:00
		val second = reserve(guest, venueId, slot(daysAhead = 3, hour = 16, hours = 2)) // 16:00-18:00

		assertEquals(201, first.status)
		assertEquals(201, second.status)
	}

	@Test
	fun `expired holds do not block search or booking`() {
		val host = signUp("Host")
		val firstGuest = signUp("First Guest")
		val secondGuest = signUp("Second Guest")
		val venueId = createVenue(host)
		val slot = slot(daysAhead = 3, hour = 18)

		val hold = reserve(firstGuest, venueId, slot)
		assertEquals(201, hold.status)
		assertEquals(0, searchCount(slot), "an active hold blocks the venue")

		clock.advance(Duration.ofMinutes(9))
		assertEquals(0, searchCount(slot), "the hold still blocks after 9 minutes")

		clock.advance(Duration.ofMinutes(2))
		assertEquals(1, searchCount(slot), "an expired hold doesn't block search")
		assertEquals(201, reserve(secondGuest, venueId, slot).status, "an expired hold doesn't block booking")
	}

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
	fun `non-owners cannot edit, delete or cancel`() {
		val host = signUp("Host")
		val guest = signUp("Guest")
		val stranger = signUp("Stranger")
		val venueId = createVenue(host)
		val reservationId = reserve(guest, venueId, slot(daysAhead = 3, hour = 18)).field("id").asLong()
		val venueBody = venueJson(name = "Taken Over")

		assertEquals(403, put("/api/v1/venues/$venueId", venueBody, stranger).status)
		assertEquals(403, delete("/api/v1/venues/$venueId", stranger).status)
		assertEquals(403, delete("/api/v1/reservations/$reservationId", stranger).status)
		assertEquals(403, delete("/api/v1/reservations/$reservationId", host).status, "the host can't cancel a guest's booking")

		assertEquals("Test Hall", get("/api/v1/venues/$venueId").field("name").asString())
		assertEquals("PENDING", get("/api/v1/reservations/$reservationId", guest).field("status").asString())
	}

	@Test
	fun `a booking needs a venue and a forward time range`() {
		val guest = signUp("Guest")
		val missing = post("/api/v1/reservations", """{"startTime":"2030-01-01T18:00:00Z"}""", guest)
		assertEquals(400, missing.status)
		assertEquals("venueId, startTime and endTime are required.", missing.field("message").asString())

		val backwards = post(
			"/api/v1/reservations",
			"""{"venueId":1,"startTime":"2030-01-01T20:00:00Z","endTime":"2030-01-01T18:00:00Z"}""",
			guest,
		)
		assertEquals("startTime must be before endTime.", backwards.field("message").asString())
	}

	@Test
	fun `booking rules are enforced in the venue's time zone`() {
		val host = signUp("Host")
		val guest = signUp("Guest")
		val venueId = createVenue(host)

		fun messageFor(slot: Slot) = reserve(guest, venueId, slot).field("message").asString()

		assertEquals("Bookings start between 6:00 AM and 11:00 PM, venue time.", messageFor(slot(3, hour = 5)))
		assertEquals("Bookings last between 1 and 12 hours.", messageFor(slot(3, hour = 8, hours = 13)))
		val offTheHour = slot(3, hour = 18).let { Slot(it.start.plusSeconds(1800), it.end.plusSeconds(1800)) }
		assertEquals("Bookings start on the hour.", messageFor(offTheHour))
		assertEquals("Pick a time in the future.", messageFor(slot(daysAhead = -1, hour = 18)))
	}

	private fun searchCount(slot: Slot): Int =
		get("/api/v1/venues/search?city=New York&startTime=${slot.start}&endTime=${slot.end}")
			.field("totalItems").asInt()

	private fun webhook(eventId: String, reservationId: Long, amount: String): Response {
		val body = """{"eventId":"$eventId","reservationId":$reservationId,"amount":$amount,"status":"SUCCEEDED"}"""
		return post("/api/v1/payments/webhook", body, headers = mapOf("X-Signature" to sign(body)))
	}

	// Signs like the payment provider would, with the test profile's webhook secret.
	private fun sign(body: String): String {
		val mac = Mac.getInstance("HmacSHA256")
		mac.init(SecretKeySpec("test-only-webhook-secret".toByteArray(), "HmacSHA256"))
		return mac.doFinal(body.toByteArray()).joinToString("") { "%02x".format(it) }
	}
}
